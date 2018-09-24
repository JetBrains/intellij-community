// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    String relativePath = file.getAbsolutePath().substring(startPackageName);
    return computeClassName(relativePath, file.getName());
  }

  @Nullable
  private String computeClassName(String relativePath, String name) {
    if (!includeUnconventionallyNamedTests) {
      if (relativePath.endsWith("Test.class")) {
        return StringUtil.trimEnd(relativePath, ".class").replace(File.separatorChar, '.');
      }
    }
    else {
      String className = name;
      if (className.endsWith(".class")) {
        int dollar = className.lastIndexOf("$");
        if (dollar != -1) {
          className = className.substring(dollar + 1);
          // most likely something like RecursionManagerTest$_testMayCache_closure5 or other anonymous class
          // may cause https://issues.apache.org/jira/browse/GROOVY-5351
          if (!Character.isUpperCase(className.charAt(0))) return null;
        }

        // A test may be named Test*, *Test, *Tests*, *TestCase, *TestSuite, *Suite, etc
        List<String> words = Arrays.asList(NameUtil.nameToWords(className));

        if (words.contains("Test") || words.contains("Tests") || words.contains("Suite")) {
          String fqn = StringUtil.trimEnd(relativePath, ".class").replace(File.separatorChar, '.');
          if (!Arrays.asList("com.intellij.tests.BootstrapTests", "com.intellij.AllTests").contains(fqn)) {
            return fqn;
          }
        }
      }
    }
    return null;
  }

  private void findAndStoreTestClasses(@NotNull File current) {
    if (current.getName().endsWith(".jar")) {
      collectClassesFromJar(current);
    }
    else if (current.isDirectory()) {
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

  private void collectClassesFromJar(@NotNull File jar) {
    try (ZipInputStream zip = new ZipInputStream(new FileInputStream(jar))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
          ContainerUtil.addIfNotNull(classNameList, computeClassName(entry.getName(), new File(entry.getName()).getName()));
        }
      }
    }
    catch (IOException e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  public Collection<String> getClasses() {
    return classNameList;
  }
}
