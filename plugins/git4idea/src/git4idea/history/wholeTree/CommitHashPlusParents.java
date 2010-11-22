/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.util.SmartList;

import java.util.List;

/**
 * @author irengrig
 */
public class CommitHashPlusParents {
  private final AbstractHash myHash;
  private final long myTime;
  private final String[] myParents;
  private final String myAuthorName;

  public CommitHashPlusParents(AbstractHash hash, String[] parents, long time, String authorName) {
    myHash = hash;
    myParents = parents;
    myTime = time;
    myAuthorName = authorName;
  }

  public CommitHashPlusParents(String hash, String[] parents, long time, String authorName) {
    myHash = AbstractHash.create(hash);
    myParents = parents;
    myTime = time;
    myAuthorName = authorName;
  }

  public long getTime() {
    return myTime;
  }

  public String getHash() {
    return myHash.getString();
  }

  public AbstractHash getAbstractHash() {
    return myHash;
  }

  public List<AbstractHash> getParents() {
    final SmartList<AbstractHash> result = new SmartList<AbstractHash>();
    for (String parent : myParents) {
      result.add(AbstractHash.create(parent));
    }
    return result;
  }

  public String getAuthorName() {
    return myAuthorName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CommitHashPlusParents that = (CommitHashPlusParents)o;

    if (!myHash.equals(that.myHash)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHash.hashCode();
  }
}
