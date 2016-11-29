/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:27:57 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


public class ClassFinder {
  private final List<String> classNameList = new ArrayList<>();
  private final int startPackageName;
  private final boolean includeUnconventionallyNamedTests;

  public ClassFinder(final File classPathRoot, final String packageRoot, boolean includeUnconventionallyNamedTests) throws IOException {
    this.includeUnconventionallyNamedTests = includeUnconventionallyNamedTests;
    startPackageName = classPathRoot.getAbsolutePath().length() + 1;
    String directoryOffset = packageRoot.replace('.', File.separatorChar);
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
      String className = file.getName();
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
          String fqn = StringUtil.trimEnd(absPath.substring(startPackageName), ".class").replace(File.separatorChar, '.');
          if (!Arrays.asList("com.intellij.tests.BootstrapTests", "com.intellij.AllTests").contains(fqn)) {
            return fqn;
          }
        }
      }
    }
    return null;
  }

  private void findAndStoreTestClasses(final File current) throws IOException {
    if (current.isDirectory()) {
      for (File file : current.listFiles()) {
        findAndStoreTestClasses(file);
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
