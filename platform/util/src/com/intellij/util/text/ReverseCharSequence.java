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
 * @author cdr
 */
public class ReverseCharSequence implements CharSequence{
  private final CharSequence mySequence;

  public ReverseCharSequence(CharSequence sequence) {
    mySequence = sequence;
  }

  public int length() {
    return mySequence.length();
  }

  public char charAt(int index) {
    return mySequence.charAt(mySequence.length()-index-1);
  }

  public CharSequence subSequence(int start, int end) {
    return new ReverseCharSequence(mySequence.subSequence(start, end));
  }
}
