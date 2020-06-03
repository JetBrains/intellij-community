// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.segments;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.Reader;

public final class PushReader {
  private final Reader mySource;
  private final IntArrayList myReadAhead = new IntArrayList();
  @NonNls
  private static final String INTERNAL_ERROR_UNEXPECTED_END_OF_PIPE = "Unexpected end of pipe";

  public PushReader(final Reader source) {
    mySource = source;
  }

  public int next() throws IOException {
    return myReadAhead.isEmpty() ? mySource.read() : myReadAhead.removeInt(myReadAhead.size() - 1);
  }

  public void pushBack(final char[] chars) {
    for (int i = chars.length - 1; i >= 0; i--) {
      final char aChar = chars[i];
      myReadAhead.add(aChar);
    }
  }

  public void close() throws IOException {
    mySource.close();
  }

  public boolean ready() throws IOException {
    return !myReadAhead.isEmpty() || mySource.ready();
  }

  public void pushBack(final int aChar) {
    myReadAhead.add(aChar);
  }

  public char[] next(final int charCount) throws IOException {
    final char[] chars = new char[charCount];
    int offset = 0;
    for (; offset < chars.length && offset < myReadAhead.size(); offset++)
      chars[offset] = (char)myReadAhead.removeInt(myReadAhead.size() - 1);

    while (offset < chars.length) {
      int bytesRead = mySource.read(chars, offset, chars.length - offset);
      if (bytesRead == -1)
        throw new IOException (INTERNAL_ERROR_UNEXPECTED_END_OF_PIPE);
      offset += bytesRead;
    }

    return chars;
  }
}
