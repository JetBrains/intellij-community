// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.editor.richcopy.view.HtmlTransferableData;
import com.intellij.openapi.editor.richcopy.view.RtfTransferableData;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import junit.framework.ComparisonFailure;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class RichCopyTest extends LightPlatformCodeInsightFixtureTestCase {
  private static final String PLATFORM_SPECIFIC_PLACEHOLDER = "___PLATFORM_SPECIFIC___";

  public void testNormalSelection() throws Exception {
    doTest(false);
  }

  public void testBlockSelection() throws Exception {
    doTest(true);
  }

  private void doTest(boolean columnMode) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".java");
    if (columnMode) {
      myFixture.performEditorAction("EditorToggleColumnMode");
    }
    myFixture.performEditorAction("EditorCopy");
    Transferable contents = CopyPasteManager.getInstance().getContents();
    assertNotNull(contents);

    assertTrue(contents.isDataFlavorSupported(HtmlTransferableData.FLAVOR));
    String expectedHtml = getFileContents(getTestName(false) + ".html");
    String actualHtml = readFully((Reader)contents.getTransferData(HtmlTransferableData.FLAVOR));
    assertMatches("HTML contents differs", expectedHtml, actualHtml);

    assertTrue(contents.isDataFlavorSupported(RtfTransferableData.FLAVOR));
    String expectedRtf = getFileContents(getTestName(false) + ".rtf" + (SystemInfo.isMac ? ".mac" : ""));
    String actualRtf = readFully((InputStream)contents.getTransferData(RtfTransferableData.FLAVOR));
    assertMatches("RTF contents differs", expectedRtf, actualRtf);
  }

  // matches 'actual' with 'expected' treating PLATFORM_SPECIFIC_PLACEHOLDER in 'expected' as .* in regexp
  private static void assertMatches(String message, String expected, String actual) {
    int posInExpected = 0;
    int posInActual = 0;
    while (posInExpected < expected.length()) {
      int placeholderPos = expected.indexOf(PLATFORM_SPECIFIC_PLACEHOLDER, posInExpected);
      if (placeholderPos < 0) {
        if (posInExpected == 0 ? actual.equals(expected) : actual.substring(posInActual).endsWith(expected.substring(posInExpected))) {
          return;
        }
        else {
          throw new ComparisonFailure(message, expected, actual);
        }
      }
      String fixedSubstring = expected.substring(posInExpected, placeholderPos);
      int matchedPosInActual = actual.indexOf(fixedSubstring, posInActual);
      if (matchedPosInActual < 0 || posInExpected == 0 && matchedPosInActual > 0) {
        throw new ComparisonFailure(message, expected, actual);
      }
      posInExpected = placeholderPos + PLATFORM_SPECIFIC_PLACEHOLDER.length();
      posInActual = matchedPosInActual + fixedSubstring.length();
    }
  }

  private static String readFully(InputStream inputStream) throws IOException {
    return new String(FileUtilRt.loadBytes(inputStream), StandardCharsets.UTF_8);
  }

  private static String readFully(Reader reader) throws IOException {
    return new String(FileUtilRt.loadText(reader, 10000));
  }

  private String getFileContents(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + fileName), true);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "editor/richcopy/";
  }
}
