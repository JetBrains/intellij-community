package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class LoadTextUtilTest extends LightPlatformTestCase {

  private static void checkGetTestByBinaryRepresentation(@NonNls String text, @NonNls String expected, @Nullable String expectedSeparator) {
    LightVirtualFile vFile = new LightVirtualFile("test.txt");
    CharSequence real = LoadTextUtil.getTextByBinaryPresentation(text.getBytes(StandardCharsets.US_ASCII), vFile);
    assertTrue("content", Comparing.equal(expected, real));
    if (expectedSeparator != null) {
      assertEquals("detected line separator", expectedSeparator, FileDocumentManager.getInstance().getLineSeparator(vFile, null));
    }
  }

  public void testSimpleLoad() {
    checkGetTestByBinaryRepresentation("test", "test", null);
  }

  public void testConvert_SlashR() {
    checkGetTestByBinaryRepresentation("test\rtest\rtest", "test\ntest\ntest", "\r");
  }

  public void testConvert_SlashN() {
    checkGetTestByBinaryRepresentation("test\ntest\ntest", "test\ntest\ntest", "\n");
  }

  public void testConvert_SlashR_SlashN() {
    checkGetTestByBinaryRepresentation("test\r\ntest\r\ntest", "test\ntest\ntest", "\r\n");
  }

  public void testConvertMostCommon() {
    checkGetTestByBinaryRepresentation("test\r\ntest\r\ntest\ntest", "test\ntest\ntest\ntest", "\r\n");
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
    String text = "A".repeat(FileSizeLimit.getDefaultContentLoadLimit() + 1);
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
    String text = "A".repeat(FileSizeLimit.getDefaultContentLoadLimit() + 1);
    byte[] stringBytes = text.getBytes(StandardCharsets.UTF_16BE);
    byte[] expectedAllBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, stringBytes);
    LightVirtualFile vFile = new LightVirtualFile("test.txt", PlainTextFileType.INSTANCE, text, StandardCharsets.UTF_16BE, 2);

    assertEquals(text, vFile.getContent().toString());
    assertVfsUtilVariousGettersAreConsistent(vFile, text, stringBytes, expectedAllBytes);
  }

  public void testConvertLineSeparatorsToSlashN_WorksForEveryLineSeparator_AndEveryCharBufferImpl() {
    String[] lineSeparators = {"\r", "\r\n", "\n"};
    String[] lines = { //random things without line separator chars:
      "line1: ab ss b ",
      "line2: zzz yyyyyyyyy",
      "",
      " ",
      " \t ",
      "-",
      "line5: f ff fff ffff, 34543 ",
      "line6: something something"
    };

    String etalon = String.join("\n", lines) + "\n";

    for (String lineSeparator : lineSeparators) {
      char[] charsContent = (String.join(lineSeparator, lines) + lineSeparator).toCharArray();

      {
        CharBuffer arrayBackedBuffer = CharBuffer.wrap(charsContent.clone());
        var result = LoadTextUtil.convertLineSeparatorsToSlashN(arrayBackedBuffer);
        String resultAsString = result.text.toString();
        assertEquals(
          "Array-backed buffer processing should lead to etalon result",
          etalon,
          resultAsString
        );
      }

      {
        CharBuffer nonArrayBackedBuffer = ByteBuffer.allocateDirect(charsContent.length * Character.BYTES)
          .asCharBuffer()
          .put(charsContent)
          .flip();
        var result = LoadTextUtil.convertLineSeparatorsToSlashN(nonArrayBackedBuffer);
        String resultAsString = result.text.toString();
        assertEquals(
          "Non-array-backed buffer processing should lead to etalon result",
          etalon,
          resultAsString
        );
      }

      {
        CharBuffer bufferWithNonZeroPosition = CharBuffer.allocate(charsContent.length * 2)
          .position(charsContent.length / 2)
          .put(charsContent)
          .flip()
          .position(charsContent.length / 2);
        var result = LoadTextUtil.convertLineSeparatorsToSlashN(bufferWithNonZeroPosition);
        String resultAsString = result.text.toString();
        assertEquals(
          "Buffer-with-non-0-position processing should lead to etalon result",
          etalon,
          resultAsString
        );
      }
    }
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
