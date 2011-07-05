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
public class GroupHeaderCommitDecorator implements CommitI {
  private final String myDecoration;
  private final CommitI myDelegate;

  public GroupHeaderCommitDecorator(String decoration, CommitI delegate) {
    myDecoration = decoration;
    myDelegate = delegate;
  }

  @Override
  public int compareByName(Commit c) {
    return myDelegate.compareByName(c);
  }

  @Override
  public void setWireNumber(int wireNumber) {
    myDelegate.setWireNumber(wireNumber);
  }

  @Override
  public String getDecorationString() {
    return myDecoration;
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
    return true;
  }

  @Override
  public <T> T selectRepository(List<T> repositories) {
    return myDelegate.selectRepository(repositories);
  }

  public CommitI getDelegate() {
    return myDelegate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroupHeaderCommitDecorator that = (GroupHeaderCommitDecorator)o;

    if (!myDecoration.equals(that.myDecoration)) return false;
    if (!myDelegate.equals(that.myDelegate)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDecoration.hashCode();
    result = 31 * result + myDelegate.hashCode();
    return result;
  }
}
