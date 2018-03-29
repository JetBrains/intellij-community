// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

public class FileOffsetsManager {

  @NotNull
  public static FileOffsetsManager getInstance() {
    return ServiceManager.getService(FileOffsetsManager.class);
  }

  private final Map<VirtualFile, LineOffsets> myLineOffsetsMap = new THashMap<>();

  private static class LineOffsets {
    private final long myFileModificationStamp;
    private final int[] myOriginalLineOffsets;
    private final int[] myConvertedLineOffsets;
    private final boolean myLineOffsetsAreTheSame;

    public LineOffsets(final long modificationStamp, @NotNull final int[] originalLineOffsets, @NotNull final int[] convertedLineOffsets) {
      assert originalLineOffsets.length > 0 && convertedLineOffsets.length > 0 && originalLineOffsets.length == convertedLineOffsets.length
        : originalLineOffsets.length + " " + convertedLineOffsets.length;

      myFileModificationStamp = modificationStamp;
      myOriginalLineOffsets = originalLineOffsets;
      myConvertedLineOffsets = convertedLineOffsets;
      myLineOffsetsAreTheSame =
        originalLineOffsets[originalLineOffsets.length - 1] == convertedLineOffsets[convertedLineOffsets.length - 1];
    }
  }

  public int getConvertedOffset(@NotNull final VirtualFile file, final int originalOffset) {
    final LineOffsets offsets = getLineOffsets(file);
    if (offsets.myLineOffsetsAreTheSame) return originalOffset;

    return getCorrespondingOffset(offsets.myOriginalLineOffsets, offsets.myConvertedLineOffsets, originalOffset);
  }

  public int getOriginalOffset(@NotNull final VirtualFile file, final int convertedOffset) {
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

  @NotNull
  private synchronized LineOffsets getLineOffsets(@NotNull final VirtualFile file) {
    LineOffsets offsets = myLineOffsetsMap.get(file);
    if (offsets != null && file.getModificationStamp() == offsets.myFileModificationStamp) {
      return offsets;
    }

    offsets = loadLineOffsets(file);
    myLineOffsetsMap.put(file, offsets);
    return offsets;
  }

  @NotNull
  // similar to com.intellij.openapi.fileEditor.impl.LoadTextUtil.loadText()
  private static LineOffsets loadLineOffsets(@NotNull final VirtualFile file) {
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

  @NotNull
  // similar to com.intellij.openapi.fileEditor.impl.LoadTextUtil.convertBytes()
  private static LineOffsets loadLineOffsets(@NotNull final byte[] bytes,
                                             @NotNull final Charset charset,
                                             final int startOffset,
                                             final long modificationStamp) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, startOffset, bytes.length - startOffset);

    CharBuffer charBuffer;
    try {
      charBuffer = charset.decode(byteBuffer);
    }
    catch (Exception e) {
      // esoteric charsets can throw any kind of exception
      charBuffer = CharBuffer.wrap(ArrayUtil.EMPTY_CHAR_ARRAY);
    }
    return loadLineOffsets(charBuffer, modificationStamp);
  }

  @NotNull
  // similar to com.intellij.openapi.fileEditor.impl.LoadTextUtil.convertLineSeparators()
  private static LineOffsets loadLineOffsets(@NotNull final CharBuffer buffer, final long modificationStamp) {
    int dst = 0;
    char prev = ' ';
    int crlfCount = 0;

    final IntArrayList originalLineOffsets = new IntArrayList();
    final IntArrayList convertedLineOffsets = new IntArrayList();
    // first line
    originalLineOffsets.add(0);
    convertedLineOffsets.add(0);

    final int length = buffer.length();
    final char[] bufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);

    for (int src = 0; src < length; src++) {
      char c = bufferArray != null ? bufferArray[src] : buffer.charAt(src);
      switch (c) {
        case '\r':
          if (bufferArray != null) {
            bufferArray[dst++] = '\n';
          }
          else {
            buffer.put(dst++, '\n');
          }
          //crCount++;
          originalLineOffsets.add(dst + crlfCount);
          convertedLineOffsets.add(dst);
          break;
        case '\n':
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
          break;
        default:
          if (bufferArray != null) {
            bufferArray[dst++] = c;
          }
          else {
            buffer.put(dst++, c);
          }
          break;
      }
      prev = c;
    }

    return new LineOffsets(modificationStamp, originalLineOffsets.toArray(), convertedLineOffsets.toArray());
  }
}
