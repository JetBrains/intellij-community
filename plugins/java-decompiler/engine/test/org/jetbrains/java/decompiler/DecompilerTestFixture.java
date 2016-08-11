/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertTrue;

public class DecompilerTestFixture {
  private File testDataDir;
  private File tempDir;
  private File targetDir;
  private ConsoleDecompiler decompiler;

  public void setUp(String... optionPairs) throws IOException {
    assertThat(optionPairs.length % 2).isEqualTo(0);

    testDataDir = new File("testData");
    if (!isTestDataDir(testDataDir)) testDataDir = new File("community/plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = new File("plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = new File("../community/plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = new File("../plugins/java-decompiler/engine/testData");
    assertTrue("current dir: " + new File("").getAbsolutePath(), isTestDataDir(testDataDir));
    testDataDir = testDataDir.getAbsoluteFile();

    //noinspection SSBasedInspection
    tempDir = File.createTempFile("decompiler_test_", "_dir");
    assertThat(tempDir.delete()).isTrue();

    targetDir = new File(tempDir, "decompiled");
    assertThat(targetDir.mkdirs()).isTrue();

    Map<String, Object> options = new HashMap<>();
    options.put(IFernflowerPreferences.LOG_LEVEL, "warn");
    options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
    options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
    options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
    options.put(IFernflowerPreferences.LITERALS_AS_IS, "1");
    options.put(IFernflowerPreferences.UNIT_TEST_MODE, "1");
    for (int i = 0; i < optionPairs.length; i += 2) {
      options.put(optionPairs[i], optionPairs[i + 1]);
    }
    decompiler = new ConsoleDecompiler(targetDir, options);
  }

  public void tearDown() {
    if (tempDir != null) {
      delete(tempDir);
    }
  }

  public File getTestDataDir() {
    return testDataDir;
  }

  public File getTempDir() {
    return tempDir;
  }

  public File getTargetDir() {
    return targetDir;
  }

  public ConsoleDecompiler getDecompiler() {
    return decompiler;
  }

  private static boolean isTestDataDir(File dir) {
    return dir.isDirectory() && new File(dir, "classes").isDirectory() && new File(dir, "results").isDirectory();
  }

  private static void delete(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File f : files) delete(f);
      }
    }
    assertTrue(file.delete());
  }

  public static void assertFilesEqual(File expected, File actual) {
    if (expected.isDirectory()) {
      String[] children = Objects.requireNonNull(expected.list());
      assertThat(actual.list()).contains(children);
      for (String name : children) {
        assertFilesEqual(new File(expected, name), new File(actual, name));
      }
    }
    else {
      assertThat(getContent(actual)).isEqualTo(getContent(expected));
    }
  }

  private static String getContent(File expected) {
    try {
      return new String(InterpreterUtil.getBytes(expected), "UTF-8").replace("\r\n", "\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}