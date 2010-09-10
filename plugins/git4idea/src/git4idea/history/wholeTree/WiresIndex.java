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

import com.intellij.openapi.vcs.BigArray;
import com.intellij.openapi.vcs.Ring;
import com.intellij.util.containers.ReadonlyList;

import java.util.Arrays;
import java.util.List;

/**
 * @author irengrig
 *
 * T - used to count wires
 * index of events -> united index (for the case when several repositories are united)
 */
public class WiresIndex {
  private final int myFrequency2Power;
  private final Ring<Integer> myRing;
  // <=
  private final BigArray<IndexPoint> myPoints;

  private int myPrevious;
  private int myPreviousAdded;

  public WiresIndex(final int frequency2Power) {
    myFrequency2Power = frequency2Power;
    myPoints = new BigArray<IndexPoint>(frequency2Power);

    myRing = new Ring.IntegerRing();
    myPrevious = -1;       // latest event idx
    myPreviousAdded = -1;  // latest filled index of index array
  }

  public void reset() {
    myRing.reset();
    myPoints.clear();
    myPreviousAdded = -1;
    myPrevious = -1;
  }

  public void accept(final int eventIdx,
                     final TreeSkeletonImpl.WireEvent event,
                     final ReadonlyList<TreeSkeletonImpl.Commit> convertor) {
    final int commitIdx = event.getCommitIdx();
    final int idx = commitIdx >> myFrequency2Power; // latest where it should be

    if ((myPreviousAdded == -1) || (idx >= (myPreviousAdded + 1))) {
      final List<Integer> used = myRing.getUsed();

      final int eventIdxToRegister = ((myPreviousAdded == -1) || (idx == (myPreviousAdded + 1))) ? eventIdx : myPrevious;
      for (int i = (myPreviousAdded + 1); i <= idx; i++) {
        myPoints.put(i, new IndexPoint(eventIdxToRegister, used.toArray(new Integer[used.size()])));
      }
      myPreviousAdded = idx;
    }
    myPrevious = eventIdx;

    performOnRing(myRing, event, convertor);
  }

  public static void performOnRing(final Ring<Integer> ring, final TreeSkeletonImpl.WireEvent event, final ReadonlyList<TreeSkeletonImpl.Commit> convertor) {
    final int[] wireEnds = event.getWireEnds();
    if (wireEnds != null) {
      for (int wireEnd : wireEnds) {
        ring.back(convertor.get(wireEnd).getWireNumber());
      }
    }
    if (event.isStart()) {
      final int commitWire = convertor.get(event.getCommitIdx()).getWireNumber();
      ring.minus(commitWire);
    }
    if (event.isEnd()) {
      final int commitWire = convertor.get(event.getCommitIdx()).getWireNumber();
      ring.back(commitWire);
    } else {
      final int[] commitsStarts = event.getCommitsStarts();
      for (int commitStart : commitsStarts) {
        final int commitWire = convertor.get(commitStart).getWireNumber();
        ring.minus(commitWire);
      }
    }
  }

  public void finish(final int lastIdx) {
    myPoints.addingFinished();
  }

  public IndexPoint getPoint(final int row) {
    final int idx = row >> myFrequency2Power;
    return myPoints.get(idx);
  }

  public int getMaxWires() {
    return myRing.getMaxNumber();
  }

  public static class IndexPoint {
    // wire index
    private final int myLessOrEqualWireEvent;
    // T[]
    private final Integer[] myWireNumbers;

    public IndexPoint(final int lessOrEqualWireEvent, final Integer[] wireNumbers) {
      myLessOrEqualWireEvent = lessOrEqualWireEvent;
      myWireNumbers = wireNumbers;
    }

    public int getLessOrEqualWireEvent() {
      return myLessOrEqualWireEvent;
    }

    public Ring<Integer> getUsedInRing() {
      return new Ring.IntegerRing(Arrays.<Integer>asList(myWireNumbers));
    }

    public Integer[] getWireNumbers() {
      return myWireNumbers;
    }
  }
}
