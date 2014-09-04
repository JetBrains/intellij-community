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
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SingleClassesTest {
  private File testDataDir;
  private File tempDir;
  private ConsoleDecompiler decompiler;

  @Before
  public void setUp() throws IOException {
    testDataDir = new File("testData");
    if (!isTestDataDir(testDataDir)) testDataDir = new File("community/plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = new File("plugins/java-decompiler/engine/testData");
    assertTrue(isTestDataDir(testDataDir));

    //noinspection SSBasedInspection
    tempDir = File.createTempFile("decompiler_test_", "_dir");
    assertTrue(tempDir.delete());
    assertTrue(tempDir.mkdirs());

    decompiler = new ConsoleDecompiler(new HashMap<String, Object>() {{
      put(IFernflowerPreferences.LOG_LEVEL, "warn");
      put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
      put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
      put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
      put(IFernflowerPreferences.LITERALS_AS_IS, "1");
    }});
  }

  @After
  public void tearDown() {
    decompiler = null;
    delete(tempDir);
    tempDir = null;
    testDataDir = null;
  }

  @Test public void testClassFields() { doTest("TestClassFields"); }
  @Test public void testClassLambda() { doTest("TestClassLambda"); }
  @Test public void testClassLoop() { doTest("TestClassLoop"); }
  @Test public void testClassSwitch() { doTest("TestClassSwitch"); }
  @Test public void testClassTypes() { doTest("TestClassTypes"); }
  @Test public void testClassVar() { doTest("TestClassVar"); }
  @Test public void testDeprecations() { doTest("TestDeprecations"); }
  @Test public void testExtendsList() { doTest("TestExtendsList"); }
  @Test public void testMethodParameters() { doTest("TestMethodParameters"); }
  @Test public void testCodeConstructs() { doTest("TestCodeConstructs"); }
  @Test public void testConstants() { doTest("TestConstants"); }
  @Test public void testEnum() { doTest("TestEnum"); }
  @Test public void testDebugSymbols() { doTest("TestDebugSymbols"); }

  private void doTest(final String testName) {
    try {
      File classFile = new File(testDataDir, "/classes/pkg/" + testName + ".class");
      assertTrue(classFile.isFile());

      decompiler.addSpace(classFile, true);
      File[] innerClasses = classFile.getParentFile().listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.matches(testName + "\\$.+\\.class");
        }
      });
      for (File inner : innerClasses) {
        decompiler.addSpace(inner, true);
      }

      decompiler.decompileContext(tempDir);

      File decompiledFile = new File(tempDir, testName + ".java");
      assertTrue(decompiledFile.isFile());

      File referenceFile = new File(testDataDir, "results/" + testName + ".dec");
      assertTrue(referenceFile.isFile());

      String decompiledContent = getContent(decompiledFile);
      String referenceContent = getContent(referenceFile);
      assertEquals(referenceContent, decompiledContent);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isTestDataDir(File dir) {
    return dir.isDirectory() && new File(dir, "classes").isDirectory() && new File(dir, "results").isDirectory();
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

  private static void delete(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File f : files) delete(f);
      }
    }
    else {
      assertTrue(file.delete());
    }
  }
}
