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

/*
 * @author max
 */
package com.intellij.util.io;

class PoolPageKey implements Comparable<PoolPageKey> {
  private RandomAccessDataFile owner;
  private long offset;

  public PoolPageKey(final RandomAccessDataFile owner, final long offset) {
    this.owner = owner;
    this.offset = offset;
  }

  public int hashCode() {
    return (int)(owner.hashCode() * 31 + offset);
  }

  public RandomAccessDataFile getOwner() {
    return owner;
  }

  public long getOffset() {
    return offset;
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  public boolean equals(final Object obj) {
    PoolPageKey k = (PoolPageKey)obj;
    return k.owner == owner && k.offset == offset;
  }

  public void setup(RandomAccessDataFile owner, long offset) {
    this.owner = owner;
    this.offset = offset;
  }

  public int compareTo(final PoolPageKey o) {
    if (owner != o.owner) {
      return owner.hashCode() - o.owner.hashCode();
    }
    return offset == o.offset ? 0 : offset - o.offset < 0 ? -1 : 1;
  }
}