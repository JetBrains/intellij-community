// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.*;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JUnit5TestRunnerUtil {
  private static final Pattern VALUE_SOURCE_PATTERN = Pattern.compile("valueSource\\s(\\d+)");
  private static Class<?> NESTED_CLASS_SELECTOR_CLASS = null;

  public static LauncherDiscoveryRequest buildRequest(String[] suiteClassNames, String[] packageNameRef, String param) {
    if (suiteClassNames.length == 0) {
      return null;
    }

    LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();


    if (suiteClassNames.length == 1 && suiteClassNames[0].charAt(0) == '@') {
      // all tests in the package specified
      try (BufferedReader reader = new BufferedReader(new FileReader(suiteClassNames[0].substring(1)))) {
        final String packageName = reader.readLine();
        if (packageName == null) return null;

        String tags = reader.readLine();
        String filters = reader.readLine();
        String line;

        List<DiscoverySelector> selectors = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
          DiscoverySelector selector = createSelector(line, null);
          if (selector != null) {
            selectors.add(selector);
          }
        }
        if (hasBrokenSelector(selectors)) {
          builder.filters(createMethodFilter(new ArrayList<>(selectors)));
          for (int i = 0; i < selectors.size(); i++) {
            DiscoverySelector selector = selectors.get(i);
            if (selector instanceof MethodSelector) {
              selectors.set(i, createClassSelector(((MethodSelector)selector).getClassName()));
            }
          }
        }
        packageNameRef[0] = packageName.isEmpty() ? "<default package>" : packageName;
        if (selectors.isEmpty()) {
          builder.selectors(DiscoverySelectors.selectPackage(packageName));
        }
        else {
          builder.selectors(selectors);
          if (!packageName.isEmpty()) {
            builder.filters(PackageNameFilter.includePackageNames(packageName));
          }
        }
        if (filters != null && !filters.isEmpty()) {
          String[] classNames = filters.split("\\|\\|");
          for (String className : classNames) {
            if (!className.contains("*")) {
              try {
                Class.forName(className, false, JUnit5TestRunnerUtil.class.getClassLoader());
              }
              catch (ClassNotFoundException e) {
                System.err.println(MessageFormat.format(ResourceBundle.getBundle("messages.RuntimeBundle").getString("junit.class.not.found"), className));
              }
            }
          }
          builder.filters(ClassNameFilter.includeClassNamePatterns(classNames));
        }
        if (tags != null && !tags.isEmpty()) {
          builder.filters(TagFilter.includeTags(tags.split(" ")));
        }
        return builder.filters(ClassNameFilter.excludeClassNamePatterns("com\\.intellij\\.rt.*", "com\\.intellij\\.junit3.*")).build();
      }
      catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
    else {
      DiscoverySelector selector = createSelector(suiteClassNames[0], packageNameRef);
      if (selector instanceof MethodSelector) {
        DiscoverySelector classSelector = createClassSelector(((MethodSelector)selector).getClassName());
        DiscoverySelector methodSelector = isNestedClassSelector(classSelector)
                                           ? DiscoverySelectors.selectMethod(((NestedClassSelector)classSelector).getNestedClassName(),
                                                                             ((MethodSelector)selector).getMethodName())
                                           : selector;
        builder.filters(createMethodFilter(Collections.singletonList(methodSelector)));
        selector = classSelector;
      }
      if (selector instanceof MethodSelector && param != null) {
        DiscoverySelector methodSelectIteration = createMethodSelectIteration(selector, param);
        if (methodSelectIteration != null) {
          return builder.selectors(methodSelectIteration).build();
        }
      }
      assert selector != null : "selector by class name is never null";
      return builder.selectors(selector).build();
    }

    return null;
  }

  private static boolean isNestedClassSelector(DiscoverySelector selector) {
    if (NESTED_CLASS_SELECTOR_CLASS == null) {
      try {
        NESTED_CLASS_SELECTOR_CLASS = Class.forName("org.junit.platform.engine.discovery.NestedClassSelector");
      }
      catch (ClassNotFoundException e) {
        return false;
      }
    }
    return NESTED_CLASS_SELECTOR_CLASS.isInstance(selector);
  }

  private static boolean loadMethodByReflection(MethodSelector selector) {
    try {
      Class<?> aClass = Class.forName(selector.getClassName());
      return ReflectionSupport.findMethod(aClass, selector.getMethodName(), selector.getMethodParameterTypes()).isPresent();
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static boolean hasBrokenSelector(List<DiscoverySelector> selectors) {
    for (DiscoverySelector selector : selectors) {
      if (selector instanceof MethodSelector && !loadMethodByReflection((MethodSelector)selector)) {
        return true;
      }
    }

    return false;
  }

  private static PostDiscoveryFilter createMethodFilter(List<DiscoverySelector> selectors) {
    return new PostDiscoveryFilter() {
      @Override
      public FilterResult apply(TestDescriptor descriptor) {
        return FilterResult.includedIf(shouldRun(descriptor),
                                       () -> descriptor.getDisplayName() + " matches",
                                       () -> descriptor.getDisplayName() + " doesn't match");
      }

      private boolean shouldRun(TestDescriptor descriptor) {
        TestSource source = descriptor.getSource().orElse(null);
        if (source instanceof MethodSource) {
          for (DiscoverySelector selector : selectors) {
            if (selector instanceof MethodSelector &&
                ((MethodSelector)selector).getMethodName().equals(((MethodSource)source).getMethodName()) &&
                (((MethodSelector)selector).getClassName().equals(((MethodSource)source).getClassName()) ||
                 inNestedClass((MethodSource)source, createClassSelector(((MethodSelector)selector).getClassName())))) {
              return true;
            }
          }
          for (DiscoverySelector selector : selectors) {
            if (selector instanceof ClassSelector && ((ClassSelector)selector).getClassName().equals(((MethodSource)source).getClassName()) ||
                inNestedClass((MethodSource)source, selector)) {
              return true;
            }
          }
          return false;
        }

        return true;
      }

      private boolean inNestedClass(MethodSource source, DiscoverySelector selector) {
        return isNestedClassSelector(selector) &&
               ((NestedClassSelector)selector).getNestedClassName().equals(source.getClassName());
      }
    };
  }

  /**
   * Unique id is prepended with prefix: @see com.intellij.execution.junit.TestUniqueId#getUniqueIdPresentation()
   * Method contains ','
   */
  private static DiscoverySelector createSelector(String line, String[] packageNameRef) {
    if (line.startsWith("\u001B")) {
      String uniqueId = line.substring("\u001B".length());
      return DiscoverySelectors.selectUniqueId(uniqueId);
    }
    else if (line.startsWith("\u002B")) {
      String directory = line.substring("\u002B".length());
      List<ClasspathRootSelector> selectors = DiscoverySelectors.selectClasspathRoots(Collections.singleton(Paths.get(directory)));
      if (selectors.isEmpty()) {
        return null;
      } else {
        return selectors.iterator().next();
      }
    }
    else if (line.contains(",")) {
      MethodSelector selector = DiscoverySelectors.selectMethod(line.replaceFirst(",", "#"));
      if (packageNameRef != null) {
        packageNameRef[0] = selector.getClassName();
      }
      return selector;
    }
    else {
      if (packageNameRef != null) {
        packageNameRef[0] = line;
      }

      return createClassSelector(line);
    }
  }

  private static DiscoverySelector createClassSelector(String line) {
    int nestedClassIdx = line.lastIndexOf("$");
    if (nestedClassIdx > 0) {
      AtomicReference<DiscoverySelector> nestedClassSelector = new AtomicReference<>();
      ReflectionSupport.tryToLoadClass(line).ifFailure(__ -> {
        nestedClassSelector.set(getNestedSelector(line, nestedClassIdx));
      });
      if (nestedClassSelector.get() != null) return nestedClassSelector.get();
    }
    return DiscoverySelectors.selectClass(line);
  }

  private static DiscoverySelector createMethodSelectIteration(DiscoverySelector methodSelector, String param) {
    Integer index = null;
    if (param != null) {
      Matcher matcher = VALUE_SOURCE_PATTERN.matcher(param);
      if (matcher.find()) {
        String group = matcher.group(1);
        try {
          index = Integer.parseInt(group);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    if (index != null) {
      try {
        return DiscoverySelectors.selectIteration(methodSelector, index);
      }
      catch (NoSuchMethodError e) {
        return null;
      }
    }
    return null;
  }
  private static NestedClassSelector getNestedSelector(String line, int nestedClassIdx) {
    String enclosingClass = line.substring(0, nestedClassIdx);
    String nestedClassName = line.substring(nestedClassIdx + 1);
    DiscoverySelector enclosingClassSelector = createClassSelector(enclosingClass);
    Class<?> klass = isNestedClassSelector(enclosingClassSelector)
                     ? ((NestedClassSelector)enclosingClassSelector).getNestedClass()
                     : ((ClassSelector)enclosingClassSelector).getJavaClass();
    Class<?> superclass = klass.getSuperclass();
    while (superclass != null) {
      for (Class<?> nested : superclass.getDeclaredClasses()) {
        if (nested.getSimpleName().equals(nestedClassName)) {
          List<Class<?>> enclosingClasses;
          if (isNestedClassSelector(enclosingClassSelector)) {
            enclosingClasses = new ArrayList<>(((NestedClassSelector)enclosingClassSelector).getEnclosingClasses());
            enclosingClasses.add(klass);
          }
          else {
            enclosingClasses = Collections.singletonList(klass);
          }
          return DiscoverySelectors.selectNestedClass(enclosingClasses, nested);
        }
      }
      superclass = superclass.getSuperclass();
    }
    return null;
  }
}
