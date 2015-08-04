/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.string;

import com.intellij.openapi.diff.LineTokenizerBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiffString extends CharArrayCharSequence {
  @NotNull public static final DiffString EMPTY = new DiffString(new char[0], 0, 0);

  @Nullable
  public static DiffString createNullable(@Nullable String string) {
    if (string == null) return null;
    return create(string);
  }

  @NotNull
  public static DiffString create(@NotNull String string) {
    if (string.isEmpty()) return EMPTY;
    return create(string.toCharArray());
  }

  @NotNull
  static DiffString create(@NotNull char[] data) {
    return create(data, 0, data.length);
  }

  @NotNull
  static DiffString create(@NotNull char[] data, int start, int length) {
    if (length == 0) return EMPTY;
    checkBounds(start, length, data.length);
    return new DiffString(data, start, length);
  }

  private DiffString(@NotNull char[] data, int start, int length) {
    super(data, start, start + length);
  }

  public boolean isEmpty() {
    return length() == 0;
  }

  private char data(int index) {
    return charAt(index);
  }

  @NotNull
  public DiffString substring(int start) {
    return substring(start, length());
  }

  @NotNull
  public DiffString substring(int start, int end) {
    if (start == 0 && end == length()) return this;
    checkBounds(start, end - start, length());
    return create(myChars, myStart + start, end - start);
  }

  @NotNull
  @Override
  public DiffString subSequence(int start, int end) {
    return substring(start, end);
  }

  @NotNull
  public DiffString copy() {
    return create(Arrays.copyOfRange(myChars, myStart, myStart + length()));
  }

  public void copyData(@NotNull char[] dst, int start) {
    checkBounds(start, length(), dst.length);
    System.arraycopy(myChars, myStart, dst, start, length());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DiffString that = (DiffString)o;

    if (length() != that.length()) return false;
    if (hashCode() != that.hashCode()) return false;
    for (int i = 0; i < length(); i++) {
      if (data(i) != that.data(i)) return false;
    }

    return true;
  }

  @Nullable
  public static DiffString concatenateNullable(@Nullable DiffString s1, @Nullable DiffString s2) {
    if (s1 == null || s2 == null) {
      if (s1 != null) return s1;
      if (s2 != null) return s2;
      return null;
    }

    return concatenate(s1, s2);
  }

  @NotNull
  public static DiffString concatenate(@NotNull DiffString s1, @NotNull DiffString s2) {
    if (s1.isEmpty()) return s2;
    if (s2.isEmpty()) return s1;

    if (s1.myChars == s2.myChars && s1.myStart + s1.length() == s2.myStart) {
      return create(s1.myChars, s1.myStart, s1.length() + s2.length());
    }

    char[] data = new char[s1.length() + s2.length()];
    System.arraycopy(s1.myChars, s1.myStart, data, 0, s1.length());
    System.arraycopy(s2.myChars, s2.myStart, data, s1.length(), s2.length());
    return create(data);
  }

  public static boolean canInplaceConcatenate(@NotNull DiffString s1, @NotNull DiffString s2) {
    if (s1.isEmpty()) return true;
    if (s2.isEmpty()) return true;

    if (s1.myChars == s2.myChars && s1.myStart + s1.length() == s2.myStart) {
      return true;
    }

    return false;
  }

  @NotNull
  public static DiffString concatenateCopying(@NotNull DiffString[] strings) {
    return concatenateCopying(strings, 0, strings.length);
  }

  @NotNull
  public static DiffString concatenateCopying(@NotNull DiffString[] strings, int start, int length) {
    checkBounds(start, length, strings.length);

    int len = 0;
    for (int i = 0; i < length; i++) {
      DiffString string = strings[start + i];
      len += string == null ? 0 : string.length();
    }

    if (len == 0) return EMPTY;

    char[] data = new char[len];
    int index = 0;
    for (int i = 0; i < length; i++) {
      DiffString string = strings[start + i];
      if (string == null || string.isEmpty()) continue;
      System.arraycopy(string.myChars, string.myStart, data, index, string.length());
      index += string.length();
    }
    return create(data);
  }

  @NotNull
  public static DiffString concatenate(@NotNull DiffString s, char c) {
    if (s.myStart + s.length() < s.myChars.length && s.data(s.length()) == c) {
      return create(s.myChars, s.myStart, s.length() + 1);
    }

    char[] data = new char[s.length() + 1];
    System.arraycopy(s.myChars, s.myStart, data, 0, s.length());
    data[s.length()] = c;
    return create(data);
  }

  @NotNull
  public static DiffString concatenate(char c, @NotNull DiffString s) {
    if (s.myStart > 0 && s.data(-1) == c) {
      return create(s.myChars, s.myStart - 1, s.length() + 1);
    }

    char[] data = new char[s.length() + 1];
    System.arraycopy(s.myChars, s.myStart, data, 1, s.length());
    data[0] = c;
    return create(data);
  }

  @NotNull
  public static DiffString concatenate(@NotNull DiffString[] strings) {
    return concatenate(strings, 0, strings.length);
  }

  @NotNull
  public static DiffString concatenate(@NotNull DiffString[] strings, int start, int length) {
    checkBounds(start, length, strings.length);

    char[] data = null;
    int startIndex = 0;
    int endIndex = 0;

    boolean linearized = true;
    for (int i = 0; i < length; i++) {
      DiffString string = strings[start + i];
      if (string == null || string.isEmpty()) continue;
      if (data == null) {
        data = string.myChars;
        startIndex = string.myStart;
        endIndex = string.myStart + string.length();
        continue;
      }
      if (data != string.myChars || string.myStart != endIndex) {
        linearized = false;
        break;
      }
      endIndex += string.length();
    }

    if (linearized) {
      if (data == null) return EMPTY;
      return create(data, startIndex, endIndex - startIndex);
    }

    return concatenateCopying(strings, start, length);
  }

  @NotNull
  public DiffString append(char c) {
    return concatenate(this, c);
  }

  @NotNull
  public DiffString preappend(char c) {
    return concatenate(c, this);
  }

  public static boolean isWhiteSpace(char c) {
    return StringUtil.isWhiteSpace(c);
  }

  public boolean isEmptyOrSpaces() {
    if (isEmpty()) return true;

    for (int i = 0; i < length(); i++) {
      if (!isWhiteSpace(data(i))) return false;
    }
    return true;
  }

  @NotNull
  public DiffString trim() {
    int start = 0;
    int end = length();

    while (start < end && isWhiteSpace(data(start))) start++;
    while (end > start && isWhiteSpace(data(end - 1))) end--;

    return substring(start, end);
  }

  @NotNull
  public DiffString trimLeading() {
    int i = 0;

    while (i < length() && isWhiteSpace(data(i))) i++;

    return substring(i, length());
  }

  @NotNull
  public DiffString trimTrailing() {
    int end = length();

    while (end > 0 && isWhiteSpace(data(end - 1))) end--;

    return substring(0, end);
  }

  @NotNull
  public DiffString getLeadingSpaces() {
    int i = 0;

    while (i < length() && data(i) == ' ') i++;

    return substring(0, i);
  }

  @NotNull
  public DiffString skipSpaces() {
    DiffString s = trim();
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (isWhiteSpace(s.data(i))) count++;
    }
    if (count == 0) return s;

    char[] data = new char[s.length() - count];
    int index = 0;
    for (int i = 0; i < s.length(); i++) {
      if (isWhiteSpace(s.data(i))) continue;
      data[index] = s.data(i);
      index++;
    }
    return create(data);
  }

  public int indexOf(char c) {
    return StringUtil.indexOf(this, c);
  }

  public boolean endsWith(char c) {
    if (isEmpty()) return false;
    return data(length() - 1) == c;
  }

  public static void checkBounds(int start, int length, int maxLength) {
    if (start < 0) {
      throw new StringIndexOutOfBoundsException(start);
    }
    if (length < 0) {
      throw new StringIndexOutOfBoundsException(length);
    }
    if (start + length > maxLength) {
      throw new StringIndexOutOfBoundsException(start + length);
    }
  }

  @NotNull
  public DiffString[] tokenize() {
    return new LineTokenizer(this).execute();
  }

  public static class LineTokenizer extends LineTokenizerBase<DiffString> {
    @NotNull private final DiffString myText;

    public LineTokenizer(@NotNull DiffString text) {
      myText = text;
    }

    @NotNull
    public DiffString[] execute() {
      ArrayList<DiffString> lines = new ArrayList<DiffString>();
      doExecute(lines);
      return ContainerUtil.toArray(lines, new DiffString[lines.size()]);
    }

    @Override
    protected void addLine(List<DiffString> lines, int start, int end, boolean appendNewLine) {
      if (appendNewLine) {
        lines.add(myText.substring(start, end).append('\n'));
      }
      else {
        lines.add(myText.substring(start, end));
      }
    }

    @Override
    protected char charAt(int index) {
      return myText.data(index);
    }

    @Override
    protected int length() {
      return myText.length();
    }

    @NotNull
    @Override
    protected String substring(int start, int end) {
      return myText.substring(start, end).toString();
    }
  }
}
