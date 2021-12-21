/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

public class CharSequenceHashInlineKeyDescriptor extends InlineKeyDescriptor<CharSequence> {
  @Override
  public CharSequence fromInt(int n) {
    return new HashWrapper(n);
  }

  @Override
  public int toInt(CharSequence s) {
    return s.hashCode();
  }

  private static class HashWrapper implements CharSequence {
    final int hashCode;

    HashWrapper(int hashCode) {
      this.hashCode = hashCode;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int length() {
      throw new UnsupportedOperationException();
    }

    @Override
    public char charAt(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull IntStream chars() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull IntStream codePoints() {
      throw new UnsupportedOperationException();
    }
  }
}
