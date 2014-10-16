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
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class SingleClassesTestBase {
  private DecompilerTestFixture fixture;

  @Before
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp(getDecompilerOptions());
  }

  @After
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  protected Map<String, Object> getDecompilerOptions() {
    return Collections.emptyMap();
  }

  protected void doTest(String testFile) {
    try {
      File classFile = new File(fixture.getTestDataDir(), "/classes/" + testFile + ".class");
      assertTrue(classFile.isFile());
      String testName = classFile.getName().substring(0, classFile.getName().length() - 6);

      ConsoleDecompiler decompiler = fixture.getDecompiler();

      for (File file : collectClasses(classFile)) decompiler.addSpace(file, true);
      decompiler.decompileContext();

      File decompiledFile = new File(fixture.getTargetDir(), testName + ".java");
      assertTrue(decompiledFile.isFile());
      File referenceFile = new File(fixture.getTestDataDir(), "results/" + testName + ".dec");
      assertTrue(referenceFile.isFile());
      compareContent(decompiledFile, referenceFile);
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

  private static void compareContent(File decompiledFile, File referenceFile) throws IOException {
    String decompiledContent = new String(InterpreterUtil.getBytes(decompiledFile), "UTF-8");

    String referenceContent = new String(InterpreterUtil.getBytes(referenceFile), "UTF-8");
    if (InterpreterUtil.IS_WINDOWS && !referenceContent.contains("\r\n")) {
      referenceContent = referenceContent.replace("\n", "\r\n");  // fix for broken Git checkout on Windows
    }

    assertEquals(referenceContent, decompiledContent);
  }
}
