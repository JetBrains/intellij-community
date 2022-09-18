// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class allows to convert offsets in a file stored on disk to offsets in the same file that IDE uses in its model
 * ({@link com.intellij.openapi.editor.Document}, {@link com.intellij.psi.PsiFile}). Offsets may be different because IDE model works with
 * normalized line separators, which are always 1 character - '\n', (see {@link LoadTextUtil#convertLineSeparatorsToSlashN(CharBuffer)}.
 * But a file stored on disk may have 2-character line breaks "\r\n".
 * <br/><br/>
 * In this class, "original offset" means offset in a file as it is stored on disk, "converted offset" - offset in a file as it is used in
 * the IDE model.
 */
@Service
public final class FileOffsetsManager {
  public static @NotNull FileOffsetsManager getInstance() {
    return ApplicationManager.getApplication().getService(FileOffsetsManager.class);
  }

  private final Map<VirtualFile, LineOffsets> myLineOffsetsMap = new HashMap<>();

  private static class LineOffsets {
    private final long myFileModificationStamp;
    private final int[] myOriginalLineOffsets;
    private final int[] myConvertedLineOffsets;
    private final boolean myLineOffsetsAreTheSame;

    LineOffsets(final long modificationStamp, final int @NotNull [] originalLineOffsets, final int @NotNull [] convertedLineOffsets) {
      assert convertedLineOffsets.length > 0 && originalLineOffsets.length == convertedLineOffsets.length
        : originalLineOffsets.length + " " + convertedLineOffsets.length;

      myFileModificationStamp = modificationStamp;
      myOriginalLineOffsets = originalLineOffsets;
      myConvertedLineOffsets = convertedLineOffsets;
      myLineOffsetsAreTheSame =
        originalLineOffsets[originalLineOffsets.length - 1] == convertedLineOffsets[convertedLineOffsets.length - 1];
    }
  }

  /**
   * @param originalOffset offset in a file as it is stored on disk
   * @return offset in the same file as it is used in IDE model (with normalized line separators)
   */
  public int getConvertedOffset(final @NotNull VirtualFile file, final int originalOffset) {
    final LineOffsets offsets = getLineOffsets(file);
    if (offsets.myLineOffsetsAreTheSame) return originalOffset;

    return getCorrespondingOffset(offsets.myOriginalLineOffsets, offsets.myConvertedLineOffsets, originalOffset);
  }

  /**
   * @param convertedOffset offset in a file as it is used in IDE model (with normalized line separators)
   * @return offset in the same file as it is stored on disk
   */
  public int getOriginalOffset(final @NotNull VirtualFile file, final int convertedOffset) {
    final LineOffsets offsets = getLineOffsets(file);
    if (offsets.myLineOffsetsAreTheSame) return convertedOffset;

    return getCorrespondingOffset(offsets.myConvertedLineOffsets, offsets.myOriginalLineOffsets, convertedOffset);
  }

  private static int getCorrespondingOffset(int[] offsets1, int[] offsets2, int offset1) {
    int line = Arrays.binarySearch(offsets1, offset1);
    if (line < 0) line = -line - 2;
    try {
      return offsets2[line] + offset1 - offsets1[line];
    }
    catch (Exception e) {
      return offset1;
    }
  }

  private synchronized @NotNull LineOffsets getLineOffsets(final @NotNull VirtualFile file) {
    LineOffsets offsets = myLineOffsetsMap.get(file);
    if (offsets != null && file.getModificationStamp() == offsets.myFileModificationStamp) {
      return offsets;
    }

    offsets = loadLineOffsets(file);
    myLineOffsetsMap.put(file, offsets);
    return offsets;
  }

  // similar to com.intellij.openapi.fileEditor.impl.LoadTextUtil.loadText()
  private static @NotNull LineOffsets loadLineOffsets(final @NotNull VirtualFile file) {
    assert !file.getFileType().isBinary();

    try {
      byte[] bytes = file.contentsToByteArray();
      final Charset charset = LoadTextUtil.detectCharsetAndSetBOM(file, bytes, file.getFileType());
      final byte[] bom = file.getBOM();
      final int bomLength = bom == null ? 0 : bom.length;
      return loadLineOffsets(bytes, charset, bomLength, file.getModificationStamp());
    }
    catch (IOException e) {
      return new LineOffsets(file.getModificationStamp(), new int[]{0}, new int[]{0});
    }
  }

  // similar to com.intellij.openapi.fileEditor.impl.LoadTextUtil.convertBytes()
  private static @NotNull LineOffsets loadLineOffsets(final byte @NotNull [] bytes,
                                                      final @NotNull Charset charset,
                                                      final int startOffset,
                                                      final long modificationStamp) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, startOffset, bytes.length - startOffset);

    CharBuffer charBuffer;
    try {
      charBuffer = charset.decode(byteBuffer);
    }
    catch (Exception e) {
      // esoteric charsets can throw any kind of exception
      charBuffer = CharBuffer.wrap(ArrayUtilRt.EMPTY_CHAR_ARRAY);
    }
    return loadLineOffsets(charBuffer, modificationStamp);
  }

  // similar to com.intellij.openapi.fileEditor.impl.LoadTextUtil.convertLineSeparatorsToSlashN()
  private static @NotNull LineOffsets loadLineOffsets(final @NotNull CharBuffer buffer, final long modificationStamp) {
    int dst = 0;
    char prev = ' ';
    int crlfCount = 0;

    final IntList originalLineOffsets = new IntArrayList();
    final IntList convertedLineOffsets = new IntArrayList();
    // first line
    originalLineOffsets.add(0);
    convertedLineOffsets.add(0);

    final int length = buffer.length();
    final char[] bufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);

    for (int src = 0; src < length; src++) {
      char c = bufferArray != null ? bufferArray[src] : buffer.charAt(src);
      switch (c) {
        case '\r' -> {
          if (bufferArray != null) {
            bufferArray[dst++] = '\n';
          }
          else {
            buffer.put(dst++, '\n');
          }
          //crCount++;
          originalLineOffsets.add(dst + crlfCount);
          convertedLineOffsets.add(dst);
        }
        case '\n' -> {
          if (prev == '\r') {
            //crCount--;
            crlfCount++;
            originalLineOffsets.set(originalLineOffsets.size() - 1, dst + crlfCount);
          }
          else {
            if (bufferArray != null) {
              bufferArray[dst++] = '\n';
            }
            else {
              buffer.put(dst++, '\n');
            }
            //lfCount++;
            originalLineOffsets.add(dst + crlfCount);
            convertedLineOffsets.add(dst);
          }
        }
        default -> {
          if (bufferArray != null) {
            bufferArray[dst++] = c;
          }
          else {
            buffer.put(dst++, c);
          }
        }
      }
      prev = c;
    }

    return new LineOffsets(modificationStamp, originalLineOffsets.toIntArray(), convertedLineOffsets.toIntArray());
  }
}
