/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * A char sequence that supports fast copying of its full or partial contents to a char array. May be useful for performance optimizations
 * @see com.intellij.util.text.CharSequenceBackedByArray
 * @see com.intellij.util.text.CharArrayUtil#getChars(CharSequence, char[], int) 
 */
public interface CharArrayExternalizable extends CharSequence {

  /**
   * Copies own character sub-sequence to the given array
   * @param start the index where to start taking chars from in this sequence
   * @param end the index where to end taking chars in this sequence
   * @param dest the array to put characters into
   * @param destPos the index where to put the characters in the dest array
   */
  void getChars(int start, int end, @NotNull char[] dest, int destPos);
}
