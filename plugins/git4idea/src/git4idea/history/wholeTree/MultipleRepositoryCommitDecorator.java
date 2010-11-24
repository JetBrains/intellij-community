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
public class MultipleRepositoryCommitDecorator implements CommitI {
  private final CommitI myDelegate;
  private final int myRepositoryIdx;

  public MultipleRepositoryCommitDecorator(CommitI delegate, int repositoryIdx) {
    myDelegate = delegate;
    myRepositoryIdx = repositoryIdx;
  }

  @Override
  public void setWireNumber(int wireNumber) {
    myDelegate.setWireNumber(wireNumber);
  }

  @Override
  public int compareByName(Commit c) {
    return myDelegate.compareByName(c);
  }

  @Override
  public String getDecorationString() {
    return myDelegate.getDecorationString();
  }

  @Override
  public AbstractHash getHash() {
    return myDelegate.getHash();
  }

  @Override
  public long getTime() {
    return myDelegate.getTime();
  }

  @Override
  public int getWireNumber() {
    return myDelegate.getWireNumber();
  }

  @Override
  public boolean holdsDecoration() {
    return myDelegate.holdsDecoration();
  }

  @Override
  public <T> T selectRepository(List<T> repositories) {
    return repositories.get(myRepositoryIdx);
  }
}
