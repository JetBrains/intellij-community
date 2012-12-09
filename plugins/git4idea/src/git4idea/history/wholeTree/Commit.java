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

import com.intellij.openapi.util.Ref;

import java.util.List;

/**
* @author irengrig
*
* comparable by time
*/
public class Commit implements Comparable<CommitI>, CommitI {
  private final AbstractHash myHash;
  private final long myTime;
  private final Ref<Integer> myAuthorIdx;

  public Commit(final String hash, long time, final Ref<Integer> authorIdx) {
    myHash = AbstractHash.create(hash);
    myTime = time;
    myAuthorIdx = authorIdx;
  }

  @Override
  public String toString() {
    return myHash.getString();
  }

  @Override
  public boolean holdsDecoration() {
    return false;
  }

  @Override
  public String getDecorationString() {
    return null;
  }

  @Override
  public <T> T selectRepository(final List<T> repositories) {
    return repositories.get(0);
  }

  @Override
  public AbstractHash getHash() {
    return myHash;
  }

  @Override
  public long getTime() {
    return myTime;
  }

  @Override
  public int getWireNumber() {
    return -1;
  }

  public void setWireNumber(int wireNumber) {
  }

  @Override
  public int compareByName(Commit c) {
    final long result = myAuthorIdx.get() - c.myAuthorIdx.get();
    return result == 0 ? 0 : (result < 0) ? -1 : 1;
  }

  @Override
  public int compareTo(CommitI o) {
    final long result = myTime - o.getTime();
    return result == 0 ? 0 : (result < 0) ? -1 : 1;
  }

  public Ref<Integer> getAuthorIdx() {
    return myAuthorIdx;
  }
}
