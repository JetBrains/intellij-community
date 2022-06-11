// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
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
      hash = h = Strings.stringHashCode(this, 0, length());
    }
    return h;
  }

  @Override
  public int length() {
    return myEnd - myStart;
  }

  @Override
  public char charAt(int index) {
    return (char)(myChars[index + myStart] & 0xff);
  }

  @NotNull
  @Override
  public CharSequence subSequence(int start, int end) {
    return start == 0 && end == length() ? this : new ByteArrayCharSequence(myChars, myStart + start, myStart + end);
  }

  @Override
  @NotNull
  public String toString() {
    return new String(myChars, myStart, length(), StandardCharsets.ISO_8859_1);
  }

  public void getChars(int start, int end, char[] dest, int pos) {
    for (int idx = start; idx < end; idx++) {
      dest[idx - start + pos] = (char)(myChars[idx + myStart] & 0xFF); 
    }
  }

  /**
   * @deprecated use {@param name} instead because of JEP 254
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull
  public static CharSequence convertToBytesIfAsciiString(@NotNull String name) {
    return name;
  }

  /**
   * @deprecated use {@param string} instead because of JEP 254
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull CharSequence convertToBytesIfPossible(@NotNull CharSequence string) {
    if (JAVA_9) return string; // see JEP 254: Compact Strings
    if (string.length() == 0) return "";
    if (string instanceof ByteArrayCharSequence) return string;
    byte[] bytes = toBytesIfPossible(string);
    return bytes == null ? string : new ByteArrayCharSequence(bytes);
  }

  private static final boolean JAVA_9;
  static {
    boolean hasModuleClass;
    try {
      Class.class.getMethod("getModule");
      hasModuleClass = true;
    }
    catch (Throwable t) {
      hasModuleClass = false;
    }
    JAVA_9 = hasModuleClass;
  }

  private byte @NotNull [] getBytes() {
    return myStart == 0 && myEnd == myChars.length ? myChars : Arrays.copyOfRange(myChars, myStart , myEnd);
  }

  private static byte @Nullable [] toBytesIfPossible(@NotNull CharSequence seq) {
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
