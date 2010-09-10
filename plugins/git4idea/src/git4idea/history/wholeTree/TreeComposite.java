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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.BigArray;
import com.intellij.openapi.vcs.CompoundNumber;
import com.intellij.openapi.vcs.ReadonlyListsMerger;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ReadonlyList;

import java.util.Comparator;
import java.util.List;

/**
 * @author irengrig
 */
public class TreeComposite<T> implements ReadonlyList<T> {
  private final List<ReadonlyList<T>> myMembers;

  private final Object myLock;
  private BigArray<CompoundNumber> myCombinedList;
  private List<BigArray<Integer>> myBackIndex;
  private final int myPackSize2Power;
  private final Comparator<Pair<CompoundNumber, T>> myComparator;

  public TreeComposite(final int packSize2Power, final Comparator<Pair<CompoundNumber, T>> comparator) {
    myPackSize2Power = packSize2Power;
    myLock = new Object();
    myComparator = comparator;
    myMembers = new SmartList<ReadonlyList<T>>();
    myCombinedList = new BigArray<CompoundNumber>(packSize2Power);
    myBackIndex = new SmartList<BigArray<Integer>>();
  }

  public void addMember(final ReadonlyList<T> convertor) {
    synchronized (myLock) {
      myMembers.add(convertor);
    }
  }

  public void clearMembers() {
    synchronized (myLock) {
      myMembers.clear();
    }
  }

  // when members are updated
  public void repack() {
    final BigArray<CompoundNumber> combinedList = new BigArray<CompoundNumber>(myPackSize2Power);
    final SmartList<BigArray<Integer>> backIndex = new SmartList<BigArray<Integer>>();
    for (int i = 0; i < myMembers.size(); i++) {
      backIndex.add(new BigArray<Integer>(myPackSize2Power));
    }

    // todo at the moment I think we don't need synch on members, further we can just put fictive list size there
    ReadonlyListsMerger.merge(myMembers, new Consumer<CompoundNumber>() {
      @Override
      public void consume(final CompoundNumber compoundNumber) {
        combinedList.add(compoundNumber);
        // it can be only next - for each list
        backIndex.get(compoundNumber.getMemberNumber()).add(compoundNumber.getIdx());
      }
    }, myComparator);

    synchronized (myLock) {
      myCombinedList = combinedList;
      myBackIndex = backIndex;
    }
  }

  @Override
  public T get(int idx) {
    synchronized (myLock) {
      final CompoundNumber compNumber = myCombinedList.get(idx);
      return myMembers.get(compNumber.getMemberNumber()).get(compNumber.getIdx());
    }
  }

  public CompoundNumber getMemberData(final int idx) {
    return myCombinedList.get(idx);
  }

  @Override
  public int getSize() {
    return myCombinedList.getSize();
  }
}
