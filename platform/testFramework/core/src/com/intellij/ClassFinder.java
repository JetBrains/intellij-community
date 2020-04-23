// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class ClassFinder {
  private final List<String> classNameList = new ArrayList<>();
  private final int startPackageName;
  private final boolean includeUnconventionallyNamedTests;

  public ClassFinder(final File classPathRoot, final String rootPackage, boolean includeUnconventionallyNamedTests) {
    this.includeUnconventionallyNamedTests = includeUnconventionallyNamedTests;
    startPackageName = classPathRoot.getAbsolutePath().length() + 1;
    String directoryOffset = rootPackage.replace('.', File.separatorChar);
    findAndStoreTestClasses(new File(classPathRoot, directoryOffset));
  }

  @Nullable
  private String computeClassName(final File file) {
    String absPath = file.getAbsolutePath();
    if (!includeUnconventionallyNamedTests) {
      if (absPath.endsWith("Test.class")) {
        return StringUtil.trimEnd(absPath.substring(startPackageName), ".class").replace(File.separatorChar, '.');
      }
    }
    else {
      String nestedClassName = file.getName();
      if (!nestedClassName.endsWith(".class")) {
        return null;
      }
      nestedClassName = StringUtil.trimEnd(nestedClassName, ".class");

      List<String> names = Arrays.asList(nestedClassName.split("\\$"));
      Collections.reverse(names);
      for (String className : names) {
        // most likely something like RecursionManagerTest$_testMayCache_closure5 or other anonymous class
        // may cause https://issues.apache.org/jira/browse/GROOVY-5351
        if (!Character.isUpperCase(className.charAt(0))) return null;

        // A test may be named Test*, *Test, *Tests*, *TestCase, *TestSuite, *Suite, etc
        List<String> words = Arrays.asList(NameUtilCore.nameToWords(className));

        if (words.contains("Test") || words.contains("Tests") || words.contains("Suite")) {
          String fqn = StringUtil.trimEnd(absPath.substring(startPackageName), ".class").replace(File.separatorChar, '.');
          if (!Arrays.asList("com.intellij.tests.BootstrapTests", "com.intellij.AllTests").contains(fqn)) {
            return fqn;
          }
        }
      }
    }
    return null;
  }

  private void findAndStoreTestClasses(@NotNull File current) {
    if (current.isDirectory()) {
      File[] files = current.listFiles();
      if (files != null) {
        for (File file : files) {
          findAndStoreTestClasses(file);
        }
      }
    }
    else {
      ContainerUtil.addIfNotNull(classNameList, computeClassName(current));
    }
  }

  public Collection<String> getClasses() {
    return classNameList;
  }
}
