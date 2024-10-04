package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class LoadTextUtilTest extends LightPlatformTestCase {
  private static void doTest(@NonNls String text, @NonNls String expected, @Nullable String expectedSeparator) {
    LightVirtualFile vFile = new LightVirtualFile("test.txt");
    CharSequence real = LoadTextUtil.getTextByBinaryPresentation(text.getBytes(StandardCharsets.US_ASCII), vFile);
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

  public void testVfsUtilLoadBytesMustIncludeBOMForRegularVirtualFile() throws IOException {
    String text = "AB";
    byte[] stringBytes = text.getBytes(StandardCharsets.UTF_16BE);
    byte[] expectedAllBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, stringBytes);

    VirtualFile tempDir = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.getTempDirectory()));
    VirtualFile vFile = VfsTestUtil.createFile(tempDir, "x.txt", expectedAllBytes);
    vFile.setCharset(StandardCharsets.UTF_16BE);

    assertVfsUtilVariousGettersAreConsistent(vFile, text, stringBytes, expectedAllBytes);
  }

  public void testVfsUtilLoadBytesMustIncludeBOMForBigRegularVirtualFile() throws IOException {
    String text = "A".repeat(FileSizeLimit.getContentLoadLimit() + 1);
    byte[] stringBytes = text.getBytes(StandardCharsets.UTF_16BE);
    byte[] expectedAllBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, stringBytes);

    VirtualFile tempDir = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.getTempDirectory()));
    VirtualFile vFile = VfsTestUtil.createFile(tempDir, "x.txt", expectedAllBytes);
    vFile.setCharset(StandardCharsets.UTF_16BE);

    assertVfsUtilVariousGettersAreConsistent(vFile, text, stringBytes, expectedAllBytes);
  }

  public void testVfsUtilLoadBytesMustIncludeBOMForLightVirtualFile() throws IOException {
    String text = "AB";
    byte[] stringBytes = text.getBytes(StandardCharsets.UTF_16BE);
    byte[] expectedAllBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, stringBytes);
    LightVirtualFile vFile = new LightVirtualFile("test.txt", PlainTextFileType.INSTANCE, text, StandardCharsets.UTF_16BE, 2);

    assertEquals(text, vFile.getContent().toString());
    assertVfsUtilVariousGettersAreConsistent(vFile, text, stringBytes, expectedAllBytes);
  }
  public void testVfsUtilLoadBytesMustIncludeBOMForBigLightVirtualFile() throws IOException {
    String text = "A".repeat(FileSizeLimit.getContentLoadLimit() + 1);
    byte[] stringBytes = text.getBytes(StandardCharsets.UTF_16BE);
    byte[] expectedAllBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, stringBytes);
    LightVirtualFile vFile = new LightVirtualFile("test.txt", PlainTextFileType.INSTANCE, text, StandardCharsets.UTF_16BE, 2);

    assertEquals(text, vFile.getContent().toString());
    assertVfsUtilVariousGettersAreConsistent(vFile, text, stringBytes, expectedAllBytes);
  }

  private static void assertVfsUtilVariousGettersAreConsistent(VirtualFile vFile, String text, byte[] stringBytes, byte[] expectedAllBytes) throws IOException {
    assertEquals(StandardCharsets.UTF_16BE, vFile.getCharset());
    assertEquals(text, VfsUtilCore.loadText(vFile));
    assertOrderedEquals(vFile.getInputStream().readAllBytes(), stringBytes);
    if (VirtualFileUtil.isTooLarge(vFile)) {
      assertThrows(FileTooBigException.class, () -> vFile.contentsToByteArray());
    }
    else {
      assertOrderedEquals(vFile.contentsToByteArray(), expectedAllBytes);
    }

    byte[] loadedBytes = VfsUtilCore.loadBytes(vFile);
    assertTrue(ArrayUtil.startsWith(expectedAllBytes, loadedBytes));
    assertOrderedEquals(VfsUtilCore.loadNBytes(vFile, (int)vFile.getLength()), expectedAllBytes);
  }
}
