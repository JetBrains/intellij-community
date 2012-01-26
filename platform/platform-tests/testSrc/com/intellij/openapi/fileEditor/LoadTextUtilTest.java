package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;

public class LoadTextUtilTest extends LightPlatformTestCase {
  private static void doTest(String source, String expected, String expectedSeparator) {
    final LightVirtualFile vFile = new LightVirtualFile("test.txt");
    final CharSequence real = LoadTextUtil.getTextByBinaryPresentation(source.getBytes(), vFile);
    assertTrue("content", Comparing.equal(expected, real));
    if (expectedSeparator != null) {
      assertEquals("detected line separator", expectedSeparator, FileDocumentManager.getInstance().getLineSeparator(vFile, null));
    }
  }

  public void testSimpleLoad() {
    doTest("test", "test", null);
  }

  public void testConvert_SlashR() {
    doTest("test\rtest\rtest", "test\ntest\ntest", "\r");
  }

  public void testConvert_SlashN() {
    doTest("test\ntest\ntest", "test\ntest\ntest", "\n");
  }

  public void testConvert_SlashR_SlashN() {
    doTest("test\r\ntest\r\ntest", "test\ntest\ntest", "\r\n");
  }

  public void testConvertMostCommon() {
    doTest("test\r\ntest\r\ntest\ntest", "test\ntest\ntest\ntest", "\r\n");
  }
}
