/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility classdef, that contains various methods for testing
 *
 * @author Ilya.Sergey
 */
public abstract class TestUtils {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.util.TestUtils");
  public static final String TEMP_FILE = "temp.groovy";
  public static final String GSP_TEMP_FILE = "temp.gsp";
  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";

  public static String getMockJdkHome() {
    return getTestDataPath() + "/mockJDK";
  }

  public static String getMockGrailsLibraryHome() {
    return getTestDataPath() + "/mockGrailsLib";
  }

  public static String getMockGroovyLibraryHome() {
    return getTestDataPath() + "/mockGroovyLib";
  }

  public static PsiFile createPseudoPhysicalFile(final Project project, final String text) throws IncorrectOperationException {
    return createPseudoPhysicalFile(project, TEMP_FILE, text);
  }

  public static PsiFile createPseudoPhysicalGspFile(final Project project, final String text) throws IncorrectOperationException {
    return createPseudoPhysicalFile(project, GSP_TEMP_FILE, text);
  }


  public static PsiFile createPseudoPhysicalFile(final Project project, final String fileName, final String text) throws IncorrectOperationException {
    return PsiManager.getInstance(project).getElementFactory().createFileFromText(
        fileName,
        FileTypeManager.getInstance().getFileTypeByFileName(fileName),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }

  private static String TEST_DATA_PATH = null;

  public static String getTestDataPath() {
    if (TEST_DATA_PATH == null) {
      ClassLoader loader = TestUtils.class.getClassLoader();
      URL resource = loader.getResource("testdata");
      try {
        TEST_DATA_PATH = new File(resource.toURI()).getPath().replace(File.separatorChar, '/');
      } catch (URISyntaxException e) {
        LOG.error(e);
        return null;
      }
    }

    return TEST_DATA_PATH;
  }

  /**
   * Removes CARET_MARKER from file text
   *
   * @param text
   * @return
   */
  public static String removeCaretMarker(String text, int myOffset) {
    int index = text.indexOf(CARET_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public static String removeBeginMarker(String text, int myOffset) {
    int index = text.indexOf(BEGIN_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + BEGIN_MARKER.length());
  }

  public static String removeEndMarker(String text, int myOffset) {
    int index = text.indexOf(END_MARKER);
    myOffset = index - 1;
    return text.substring(0, index) + text.substring(index + END_MARKER.length());
  }

  public static String transformForRelPathTest(String myTestFilePath) throws Exception {
    String testName = myTestFilePath;
    final int dotIdx = testName.indexOf('.');
    if (dotIdx >= 0) {
      testName = testName.substring(0, dotIdx);
    }
    return testName;
  }

  public static String[] getInputAndResult(File myTestFile) throws IOException {
    String content = new String(FileUtil.loadFileText(myTestFile));
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<String>();

    int separatorIndex;
    content = StringUtil.replace(content, "\r", ""); // for MACs

    // Adding input  before -----
    while ((separatorIndex = content.indexOf("-----")) >= 0) {
      input.add(content.substring(0, separatorIndex - 1));
      content = content.substring(separatorIndex);
      while (StringUtil.startsWithChar(content, '-') ||
          StringUtil.startsWithChar(content, '\n')) {
        content = content.substring(1);
      }
    }
    // Result - after -----
    String result = content;
    while (StringUtil.startsWithChar(result, '-') ||
        StringUtil.startsWithChar(result, '\n') ||
        StringUtil.startsWithChar(result, '\r')) {
      result = result.substring(1);
    }

    Assert.assertTrue("No data found in source file", input.size() > 0);
    Assert.assertNotNull(result);
    Assert.assertNotNull(input);

    String inputString = input.toArray(new String[input.size()])[0];
    return new String[]{inputString, result};
  }
}
