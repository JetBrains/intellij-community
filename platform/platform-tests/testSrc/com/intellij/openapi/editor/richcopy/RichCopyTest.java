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
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.richcopy.view.HtmlTransferableData;
import com.intellij.openapi.editor.richcopy.view.RtfTransferableData;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import junit.framework.ComparisonFailure;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

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
    String expectedRtf = getFileContents(getTestName(false) + ".rtf");
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
    return new String(FileUtilRt.loadBytes(inputStream));
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
    return PathManagerEx.getHomePath(getClass()) + "/platform/platform-tests/testData/editor/richcopy/";
  }
}
