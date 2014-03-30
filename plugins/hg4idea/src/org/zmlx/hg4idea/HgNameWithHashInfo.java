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
package org.zmlx.hg4idea;

import com.google.common.base.Objects;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author Nadya Zabrodina
 */

/**
 * Used for storing branch information from repository internal hg files, f.e.  branchheads, bookmarks
 * see HgRepositoryReader
 */
public class HgNameWithHashInfo {

  @NotNull protected final String myName;
  @NotNull private final Hash myHash;

  public HgNameWithHashInfo(@NotNull String name, @NotNull Hash hash) {
    myName = name;
    myHash = hash;
  }

  /**
   * <p>Returns the hash on which this bookmark or tag is reference to.</p>
   */
  @NotNull
  public Hash getHash() {
    return myHash;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HgNameWithHashInfo info = (HgNameWithHashInfo)o;
    return (myName.equals(info.myName)) && myHash.equals(info.myHash);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myName, myHash);
  }
}
