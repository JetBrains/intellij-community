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

package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

/**
 * A char sequence based on a char array. May be used for performance optimizations.
 * 
 * @author Maxim.Mossienko
 * @see com.intellij.util.text.CharArrayExternalizable
 * @see com.intellij.util.text.CharArrayUtil#getChars(CharSequence, char[], int) 
 * @see com.intellij.util.text.CharArrayUtil#fromSequenceWithoutCopying(CharSequence)  
 */
public interface CharSequenceBackedByArray extends CharSequence {
  // NOT guaranteed to return the array of the length of the original charSequence.length() - may be more for performance reasons.
  @NotNull
  char[] getChars();

  void getChars(@NotNull char[] dst, int dstOffset);
}
