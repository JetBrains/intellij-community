// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

// we must return string on subSequence() - JsonReaderEx will call toString in any case
public class CharSequenceBackedByChars extends CharArrayCharSequence {
  public ByteBuffer getByteBuffer() {
    return StandardCharsets.UTF_8.encode(CharBuffer.wrap(myChars, myStart, length()));
  }

  public CharSequenceBackedByChars(CharBuffer charBuffer) {
    super(charBuffer.array(), charBuffer.arrayOffset(), charBuffer.position());
  }

  public CharSequenceBackedByChars(char[] chars, int start, int end) {
    super(chars, start, end);
  }

  public CharSequenceBackedByChars(char[] chars) {
    super(chars);
  }

  @Override
  public @NotNull CharSequence subSequence(int start, int end) {
    if (start == 0 && end == length()) {
      return this;
    }
    else {
      return new String(myChars, myStart + start, end - start);
    }
  }
}
