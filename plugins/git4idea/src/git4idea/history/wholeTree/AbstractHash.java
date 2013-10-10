/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 */
@Deprecated
public class AbstractHash {

  @NotNull private final Hash myHash;

  private AbstractHash(@NotNull Hash hash) {
    myHash = hash;
  }

  @NotNull
  public static AbstractHash create(final String hash) {
    return new AbstractHash(HashImpl.build(hash));
  }

  public String getString() {
    return myHash.asString();
  }

  @Override
  public String toString() {
    return getString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractHash hash = (AbstractHash)o;

    if (!myHash.equals(hash.myHash)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHash.hashCode();
  }

  public static boolean hashesEqual(@NotNull final AbstractHash hash1, @NotNull final AbstractHash hash2) {
    if (hash1.equals(hash2)) return true;
    final String s1 = hash1.getString();
    final String s2 = hash2.getString();
    if (s1.startsWith(s2) || s2.startsWith(s1)) return true;
    return false;
  }
}
