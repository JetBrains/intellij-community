// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.text.CharArrayUtil;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class FileSetTestCase extends TestSuite {
  private final File[] myFiles;
  protected Project myProject;
  private Pattern myPattern;

  public FileSetTestCase(@NotNull String path) {
    File f = new File(path);
    if (f.isDirectory()) {
      myFiles = f.listFiles();
    }
    else if (f.exists()) {
      myFiles = new File[] {f};
    }
    else {
      throw new IllegalArgumentException("invalid path: "     + path);
    }

    final String pattern = System.getProperty("fileset.pattern");
    myPattern = pattern != null ? Pattern.compile(pattern) : null;
    addAllTests();
  }

  protected FileSetTestCase(File @NotNull [] files) {
    myFiles = files;
    addAllTests();
  }

  protected void setUp() {

  }

  protected void tearDown() {
    myProject = null;
  }

  private void addAllTests() {
    for (File file : myFiles) {
      if (file.isFile()) {
        addFileTest(file);
      }
    }
  }

  public abstract String transform(String testName, String[] data) throws Exception;

  @Override
  public String getName() {
    return getClass().getName();
  }

  private void addFileTest(File file) {
    if (!StringUtil.startsWithChar(file.getName(), '_') && !"CVS".equals(file.getName())) {
      if (myPattern != null && !myPattern.matcher(file.getPath()).matches()){
        return;
      }
      final ActualTest t = new ActualTest(file, createTestName(file));
      addTest(t);
    }
  }

  protected String loadFile(File testFile) throws IOException {
    return FileUtil.loadFile(testFile);
  }

  protected String getDelimiter() {
    return "---";
  }

  private static String createTestName(File testFile) {
    return testFile.getName();
  }

  @SuppressWarnings({"JUnitTestCaseWithNoTests", "UnconstructableJUnitTestCase"})
  private class ActualTest extends LightPlatformTestCase {
    private final File myTestFile;
    private final String myTestName;

    ActualTest(File testFile, String testName) {
      myTestFile = testFile;
      myTestName = testName;
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      FileSetTestCase.this.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
      try {
        FileSetTestCase.this.tearDown();
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        super.tearDown();
      }
    }

    @Override
    protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
      String content = loadFile(myTestFile);
      assertNotNull(content);

      List<String> input = new ArrayList<>();

      content = StringUtil.replace(content, "\r", "");

      int currentIndex = 0;
      int separatorIndex;
      String delimiter = getDelimiter();
      while ((separatorIndex = content.indexOf(delimiter, currentIndex)) >= 0) {
        input.add(content.substring(currentIndex, separatorIndex));
        currentIndex = separatorIndex + delimiter.length();
        currentIndex = CharArrayUtil.shiftForward(content, currentIndex, "-\n");
      }

      assertFalse("No data found in source file", input.isEmpty());

      int expectedOffset = currentIndex;

      myProject = getProject();
      String testName = myTestFile.getName();
      final int dotIdx = testName.indexOf('.');
      if (dotIdx >= 0) {
        testName = testName.substring(0, dotIdx);
      }

      final String transformed = transform(testName, ArrayUtilRt.toStringArray(input));
      String result = content.substring(0, expectedOffset) + transformed;

      UsefulTestCase.assertSameLinesWithFile(myTestFile.getAbsolutePath(), result, true);
    }

    @NotNull
    @Override
    protected String getTestName(final boolean lowercaseFirstLetter) {
      return "";
    }

    public String toString() {
      return myTestFile.getAbsolutePath() + " ";
    }

    @Override
    protected void resetAllFields() {
      // Do nothing otherwise myTestFile will be nulled out before getName() is called.
    }

    @Override
    public String getName() {
      return myTestName;
    }
  }
}
