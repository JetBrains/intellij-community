/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea;

import org.jetbrains.annotations.NotNull;

/**
 * Encapsulation of the hash representing an object in Git.
 *
 * @author Kirill Likhodedov
 */
public class Hash {

  @NotNull private final String myHash;

  private Hash(@NotNull String hash) {
    myHash = hash;
  }

  @NotNull
  public static Hash create(@NotNull String hash) {
    return new Hash(hash);
  }

  @NotNull
  public String asString() {
    return myHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Hash hash = (Hash)o;

    if (!myHash.equals(hash.myHash)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHash.hashCode();
  }

  @Override
  public String toString() {
    return myHash;
  }

}
