/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.text;

/**
 * @author max
 */
public class MergingCharSequence implements CharSequence {
  private final CharSequence s1;
  private final CharSequence s2;

  public MergingCharSequence(final CharSequence s1, final CharSequence s2) {
    this.s1 = s1;
    this.s2 = s2;
  }

  public int length() {
    return s1.length() + s2.length();
  }

  public char charAt(int index) {
    if (index < s1.length()) return s1.charAt(index);
    return s2.charAt(index - s1.length());
  }

  public CharSequence subSequence(int start, int end) {
    if (start < s1.length() && end < s1.length()) return s1.subSequence(start, end);
    if (start >= s1.length() && end >= s1.length()) return s2.subSequence(start - s1.length(), end - s1.length());
    return new MergingCharSequence(s1.subSequence(start, s1.length()), s2.subSequence(0, end - s1.length()));
  }

  public String toString() {
    return s1.toString() + s2.toString();
  }
}
