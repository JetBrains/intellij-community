// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ReflectionUtils;
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
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

public class JUnit5TestRunnerUtil {
  private static final String[] DISABLED_ANNO = {"org.junit.jupiter.api.Disabled"};

  private static final String[] DISABLED_COND_ANNO = {
    "org.junit.jupiter.api.condition.DisabledOnJre",
    "org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable",
    "org.junit.jupiter.api.condition.DisabledIfSystemProperty",
    "org.junit.jupiter.api.condition.DisabledOnOs"
  };

  private static final String[] SCRIPT_COND_ANNO =
    {
      "org.junit.jupiter.api.condition.DisabledIf",
      "org.junit.jupiter.api.condition.EnabledIf"
    };

  private static final String[] ENABLED_COND_ANNO = {
    "org.junit.jupiter.api.condition.EnabledOnJre",
    "org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable",
    "org.junit.jupiter.api.condition.EnabledIfSystemProperty",
    "org.junit.jupiter.api.condition.EnabledOnOs"
  };

  public static LauncherDiscoveryRequest buildRequest(String[] suiteClassNames, String[] packageNameRef) {
    if (suiteClassNames.length == 0) {
      return null;
    }

    LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();


    if (suiteClassNames.length == 1 && suiteClassNames[0].charAt(0) == '@') {
      // all tests in the package specified
      try {
        BufferedReader reader = new BufferedReader(new FileReader(suiteClassNames[0].substring(1)));
        try {
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
          packageNameRef[0] = packageName.length() == 0 ? "<default package>" : packageName;
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
        finally {
          reader.close();
        }
      }
      catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
    else {
      String disableDisabledCondition = getDisabledConditionValue(suiteClassNames[0]);
      if (disableDisabledCondition != null) {
        builder = builder.configurationParameter("junit.jupiter.conditions.deactivate", disableDisabledCondition);
      }

      DiscoverySelector selector = createSelector(suiteClassNames[0], packageNameRef);
      if (selector instanceof MethodSelector) {
        try {
          ((MethodSelector)selector).getJavaMethod();
        }
        catch (Throwable e) {
          DiscoverySelector classSelector = createClassSelector(((MethodSelector)selector).getClassName());
          DiscoverySelector methodSelector = classSelector instanceof NestedClassSelector
                                ? DiscoverySelectors.selectMethod(((NestedClassSelector)classSelector).getNestedClassName(),
                                                                  ((MethodSelector)selector).getMethodName())
                                : selector;
          builder.filters(createMethodFilter(Collections.singletonList(methodSelector)));
          selector = classSelector;
        }
      }
      assert selector != null : "selector by class name is never null";
      return builder.selectors(selector).build();
    }

    return null;
  }

  private static boolean hasBrokenSelector(List<DiscoverySelector> selectors) {
    for (DiscoverySelector selector : selectors) {
      if (selector instanceof MethodSelector) {
        try {
          ((MethodSelector)selector).getJavaMethod();
        }
        catch (Throwable e) {
          return true;
        }
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
        return selector instanceof NestedClassSelector &&
               ((NestedClassSelector)selector).getNestedClassName().equals(source.getClassName());
      }
    };
  }

  public static String getDisabledConditionValue(String name) {
    int commaIdx = name.indexOf(",");
    String className = name.substring(0, commaIdx < 0 ? name.length() : commaIdx);
    String methodName = commaIdx > 0 ? name.substring(commaIdx + 1) : null;
    try {
      ClassLoader loader = JUnit5TestRunnerUtil.class.getClassLoader();
      Class<?> testClass = Class.forName(className, false, loader);

      String disabledCondition = getDisabledCondition(loader, testClass);
      if (disabledCondition != null) {
        return disabledCondition;
      }

      if (methodName != null) {
        int paramIdx = methodName.indexOf("(");
        Method m;
        if (paramIdx < 0) {
          m = testClass.getDeclaredMethod(methodName);
        }
        else {
          if (!methodName.endsWith(")")) return null;
          String paramsString = methodName.substring(paramIdx + 1, methodName.length() - 1);
          String[] params = paramsString.split(",");
          Class<?>[] paramTypes = new Class[params.length];
          for (int i = 0; i < params.length; i++) {
            paramTypes[i] = Class.forName(params[i], false, loader);
          }
          m = testClass.getDeclaredMethod(methodName.substring(0, paramIdx), paramTypes);
        }
        disabledCondition = getDisabledCondition(loader, m);
        if (disabledCondition != null) {
          return disabledCondition;
        }
      }

      return null;
    }
    catch (Throwable ignore) { }
    return null;
  }

  private static String getDisabledCondition(ClassLoader loader, AnnotatedElement annotatedElement) {
    if (isDisabledCondition(DISABLED_COND_ANNO, loader, annotatedElement)) {
      return "org.junit.*Disabled*Condition";
    }

    if (isDisabledCondition(ENABLED_COND_ANNO, loader, annotatedElement)) {
      return "org.junit.*Enabled*Condition";
    }

    if (isDisabledCondition(SCRIPT_COND_ANNO, loader, annotatedElement)) {
      return "org.junit.*DisabledIfCondition";
    }

    if (isDisabledCondition(DISABLED_ANNO, loader, annotatedElement)) {
      return "org.junit.*DisabledCondition";
    }
    return null;
  }

  private static boolean isDisabledCondition(String[] anno, ClassLoader loader, AnnotatedElement annotatedElement) {
    for (String disabledAnnotationName : anno) {
      try {
        Class<? extends Annotation> disabledAnnotation = (Class<? extends Annotation>)Class.forName(disabledAnnotationName, false, loader);
        if (AnnotationUtils.findAnnotation(annotatedElement, disabledAnnotation).isPresent()) {
          return true;
        }
      } catch (ClassNotFoundException e) {
        // TODO we just ignore it. In later Junit5 versions some condition annotations were removed, i.e. @DisabledIf
      }
    }
    return false;
  }

  /**
   * Unique id is prepended with prefix: @see com.intellij.execution.junit.TestUniqueId#getUniqueIdPresentation()
   * Method contains ','
   */
  protected static DiscoverySelector createSelector(String line, String[] packageNameRef) {
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
      ReflectionUtils.tryToLoadClass(line).ifFailure(__ -> {
        nestedClassSelector.set(getNestedSelector(line, nestedClassIdx));
      });
      if (nestedClassSelector.get() != null) return nestedClassSelector.get();
    }
    
    return DiscoverySelectors.selectClass(line);
  }

  private static NestedClassSelector getNestedSelector(String line,
                                                       int nestedClassIdx) {
    String enclosingClass = line.substring(0, nestedClassIdx);
    String nestedClassName = line.substring(nestedClassIdx + 1);
    DiscoverySelector enclosingClassSelector = createClassSelector(enclosingClass);
    Class<?> klass = enclosingClassSelector instanceof NestedClassSelector
                     ? ((NestedClassSelector)enclosingClassSelector).getNestedClass()
                     : ((ClassSelector)enclosingClassSelector).getJavaClass();
    Class<?> superclass = klass.getSuperclass();
    while (superclass != null) {
      for (Class<?> nested : superclass.getDeclaredClasses()) {
        if (nested.getSimpleName().equals(nestedClassName)) {
          List<Class<?>> enclosingClasses;
          if (enclosingClassSelector instanceof NestedClassSelector) {
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
