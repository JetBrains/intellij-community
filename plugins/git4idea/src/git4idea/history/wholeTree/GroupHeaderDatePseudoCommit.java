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

import java.util.List;

/**
 * @author irengrig
 */
public class GroupHeaderDatePseudoCommit implements CommitI {
  private final long myTime;
  private final String myGroupName;

  public GroupHeaderDatePseudoCommit(final String groupName, long time) {
    myGroupName = groupName;
    myTime = time;
  }

  @Override
  public int compareByName(Commit c) {
    return 0;
  }
  @Override
  public boolean holdsDecoration() {
    return true;
  }
  @Override
  public String getDecorationString() {
    return myGroupName;
  }
  @Override
  public <T> T selectRepository(List<T> repositories) {
    return repositories.get(0);
  }

  @Override
  public AbstractHash getHash() {
    return null;
  }

  @Override
  public long getTime() {
    return myTime;
  }

  @Override
  public int getWireNumber() {
    return 0;
  }

  @Override
  public void setWireNumber(int wireNumber) {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroupHeaderDatePseudoCommit that = (GroupHeaderDatePseudoCommit)o;

    if (myTime != that.myTime) return false;
    if (!myGroupName.equals(that.myGroupName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int)(myTime ^ (myTime >>> 32));
    result = 31 * result + myGroupName.hashCode();
    return result;
  }
}
