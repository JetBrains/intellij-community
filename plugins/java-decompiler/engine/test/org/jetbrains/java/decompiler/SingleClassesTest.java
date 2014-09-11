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
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SingleClassesTest {
  private DecompilerTestFixture fixture;

  @Before
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp();
  }

  @After
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  @Test public void testClassFields() { doTest("TestClassFields"); }
  @Test public void testClassLambda() { doTest("TestClassLambda"); }
  @Test public void testClassLoop() { doTest("TestClassLoop"); }
  @Test public void testClassSwitch() { doTest("TestClassSwitch"); }
  @Test public void testClassTypes() { doTest("TestClassTypes"); }
  @Test public void testClassVar() { doTest("TestClassVar"); }
  @Test public void testClassNestedInitializer() { doTest("TestClassNestedInitializer"); }
  @Test public void testDeprecations() { doTest("TestDeprecations"); }
  @Test public void testExtendsList() { doTest("TestExtendsList"); }
  @Test public void testMethodParameters() { doTest("TestMethodParameters"); }
  @Test public void testCodeConstructs() { doTest("TestCodeConstructs"); }
  @Test public void testConstants() { doTest("TestConstants"); }
  @Test public void testEnum() { doTest("TestEnum"); }
  @Test public void testDebugSymbols() { doTest("TestDebugSymbols"); }

  private void doTest(final String testName) {
    try {
      File classFile = new File(fixture.getTestDataDir(), "/classes/pkg/" + testName + ".class");
      assertTrue(classFile.isFile());

      ConsoleDecompiler decompiler = fixture.getDecompiler();
      for (File inner : collectClasses(classFile)) {
        decompiler.addSpace(inner, true);
      }

      decompiler.decompileContext();

      File decompiledFile = new File(fixture.getTargetDir(), testName + ".java");
      assertTrue(decompiledFile.isFile());

      File referenceFile = new File(fixture.getTestDataDir(), "results/" + testName + ".dec");
      assertTrue(referenceFile.isFile());

      String decompiledContent = getContent(decompiledFile);
      String referenceContent = getContent(referenceFile);
      assertEquals(referenceContent, decompiledContent);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static List<File> collectClasses(File classFile) {
    List<File> files = new ArrayList<File>();
    files.add(classFile);

    File parent = classFile.getParentFile();
    if (parent != null) {
      final String pattern = classFile.getName().replace(".class", "") + "\\$.+\\.class";
      File[] inner = parent.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.matches(pattern);
        }
      });
      if (inner != null) Collections.addAll(files, inner);
    }

    return files;
  }

  private static String getContent(File file) throws IOException {
    Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
    try {
      char[] buff = new char[16 * 1024];
      StringBuilder content = new StringBuilder();
      int n;
      while ((n = reader.read(buff)) > 0) {
        content.append(buff, 0, n);
      }
      return content.toString();
    }
    finally {
      reader.close();
    }
  }
}
