// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ReviseWhenPortedToJDK("9")
public final class ByteArrayCharSequence implements CharSequenceWithStringHash {
  private final int myStart;
  private final int myEnd;
  private transient int hash;
  private final byte[] myChars;

  public ByteArrayCharSequence(byte @NotNull [] chars) {
    this(chars, 0, chars.length);
  }
  public ByteArrayCharSequence(byte @NotNull [] chars, int start, int end) {
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = Strings.stringHashCode(this, myStart, myEnd);
    }
    return h;
  }

  @Override
  public final int length() {
    return myEnd - myStart;
  }

  @Override
  public final char charAt(int index) {
    return (char)(myChars[index + myStart] & 0xff);
  }

  @NotNull
  @Override
  public CharSequence subSequence(int start, int end) {
    return start == 0 && end == length() ? this : new CharSequenceSubSequence(this, start, end);
  }

  @Override
  @NotNull
  public String toString() {
    return new String(myChars, myStart, length(), StandardCharsets.ISO_8859_1);
  }

  /**
   * @deprecated use {@link #convertToBytesIfPossible(CharSequence)} instead
   */
  @Deprecated
  @NotNull
  public static CharSequence convertToBytesIfAsciiString(@NotNull String name) {
    return convertToBytesIfPossible(name);
  }

  /**
   * @return instance of {@link ByteArrayCharSequence} if the supplied string can be stored internally
   * as a byte array of 8-bit code points (for more compact representation); its {@code string} argument otherwise
   */
  @NotNull
  public static CharSequence convertToBytesIfPossible(@NotNull CharSequence string) {
    if (SystemInfoRt.IS_AT_LEAST_JAVA9) return string; // see JEP 254: Compact Strings
    if (string.length() == 0) return "";
    if (string instanceof ByteArrayCharSequence) return string;
    byte[] bytes = toBytesIfPossible(string);
    return bytes == null ? string : new ByteArrayCharSequence(bytes);
  }

  byte @NotNull [] getBytes() {
    return myStart == 0 && myEnd == myChars.length ? myChars : Arrays.copyOfRange(myChars, myStart , myEnd);
  }

  static byte @Nullable [] toBytesIfPossible(@NotNull CharSequence seq) {
    if (seq instanceof ByteArrayCharSequence) {
      return ((ByteArrayCharSequence)seq).getBytes();
    }
    byte[] bytes = new byte[seq.length()];
    char[] chars = CharArrayUtil.fromSequenceWithoutCopying(seq);
    if (chars == null) {
      for (int i = 0; i < bytes.length; i++) {
        char c = seq.charAt(i);
        if ((c & 0xff00) != 0) {
          return null;
        }
        bytes[i] = (byte)c;
      }
    }
    else {
      for (int i = 0; i < bytes.length; i++) {
        char c = chars[i];
        if ((c & 0xff00) != 0) {
          return null;
        }
        bytes[i] = (byte)c;
      }
    }
    return bytes;
  }
}
