// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class DecompilerTestFixture {
  private File testDataDir;
  private File tempDir;
  private File targetDir;
  private TestConsoleDecompiler decompiler;

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
    decompiler = new TestConsoleDecompiler(targetDir, options);
  }

  public void tearDown() {
    if (tempDir != null) {
      delete(tempDir);
    }
    decompiler.close();
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

  // cache zip files
  private static class TestConsoleDecompiler extends ConsoleDecompiler {
    private final HashMap<String, ZipFile> zipFiles = new HashMap<>();

    public TestConsoleDecompiler(File destination, Map<String, Object> options) {
      super(destination, options, new PrintStreamLogger(System.out));
    }

    @Override
    public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
      File file = new File(externalPath);
      if (internalPath == null) {
        return InterpreterUtil.getBytes(file);
      }
      else {
        ZipFile archive = zipFiles.get(file.getName());
        if (archive == null) {
          archive = new ZipFile(file);
          zipFiles.put(file.getName(), archive);
        }
        ZipEntry entry = archive.getEntry(internalPath);
        if (entry == null) throw new IOException("Entry not found: " + internalPath);
        return InterpreterUtil.getBytes(archive, entry);
      }
    }

    void close() {
      for (ZipFile file : zipFiles.values()) {
        try {
          file.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
      zipFiles.clear();
    }
  }
}