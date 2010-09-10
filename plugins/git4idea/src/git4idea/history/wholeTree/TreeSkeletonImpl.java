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
import com.intellij.openapi.vcs.Ring;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.containers.ReadonlyList;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author irengrig
 */
public class TreeSkeletonImpl implements TreeSkeleton {
  // just commit hashes
  private final BigArray<Commit> myList;

  // hierarchy structure events
  private final List<WireEvent> myWireEvents;

  private final WiresIndex myWiresIndex;
  private final ReadonlyList<Commit> myAsCommitList;

  public TreeSkeletonImpl(final int size2Power, final int wireEventsIdxSize2Power) {
    // some defense. small values for tests
    assert (size2Power < 16) && (wireEventsIdxSize2Power < 16) && (size2Power > 1) && (wireEventsIdxSize2Power > 1);

    myList = new BigArray<Commit>(size2Power);
    myWireEvents = new LinkedList<WireEvent>(); // todo can use another structure, a list of arrays?
    myWiresIndex = new WiresIndex(wireEventsIdxSize2Power);
    myAsCommitList = new ReadonlyList<Commit>() {
      @Override
      public Commit get(int idx) {
        return TreeSkeletonImpl.this.get(idx);
      }

      @Override
      public int getSize() {
        return TreeSkeletonImpl.this.getSize();
      }
    };
  }

  public void wireStarts(final int row) {
    final WireEvent e = new WireEvent(row, new int[]{-1});
    myWireEvents.add(e);
  }

  public void wireEnds(final int row) {
    modifyWireEvent(row, new Consumer<WireEvent>() {
      @Override
      public void consume(WireEvent wireEvent) {
        wireEvent.addStart(-1);
      }
    });
  }

  public void refreshIndex() {
    myWiresIndex.reset();
    for (int i = 0; i < myWireEvents.size(); i++) {
      final WireEvent event = myWireEvents.get(i);
      myWiresIndex.accept(i, event, myAsCommitList);
    }
  }

  public void addWireEvent(final int row, final int[] branched) {
    final WireEvent wireEvent = new WireEvent(row, branched);
    myWireEvents.add(wireEvent);
  }

  public void addStartToEvent(final int row, final int parentRow) {
    modifyWireEvent(row, new Consumer<WireEvent>() {
      @Override
      public void consume(WireEvent wireEvent) {
        wireEvent.addStart(parentRow);
      }
    });
  }

  public void parentWireEnds(final int row, final int parentRow) {
    modifyWireEvent(row, new Consumer<WireEvent>() {
      @Override
      public void consume(WireEvent wireEvent) {
        wireEvent.addWireEnd(parentRow);
      }
    });
  }

  private void modifyWireEvent(final int row, final Consumer<WireEvent> wireEventConsumer) {
    final int foundIdx = Collections.binarySearch(myWireEvents, new WireEvent(row, null), SearchWireEventsComparator.getInstance());
    // exact coinsidence
    WireEvent event;
    if (foundIdx >= 0) {
      event = myWireEvents.get(foundIdx);
    } else {
      event = new WireEvent(row, null);
      final int index = - foundIdx - 1;
      myWireEvents.add(index, event);
    }
    wireEventConsumer.consume(event);
  }

  /*public void markBreaks(final Collection<Integer> breaks) {
    for (Integer aBreak : breaks) {
      modifyWireEvent(aBreak, new Consumer<WireEvent>() {
        @Override
        public void consume(WireEvent wireEvent) {
          wireEvent.addStart(-1);
        }
      });
    }
  }*/
  
  // todo not very well
  public void commitsAdded(final int rowThatWasLast) {
    myList.addingFinished();
  }

  public void addCommit(final int row, final String hash, final int wireNumber, final long time) {
    myList.put(row, new Commit(hash.getBytes(), wireNumber, time));
  }

  @Nullable
  @Override
  public Ring<Integer> getUsedWires(final int row) {
    if (myWireEvents.isEmpty()) return null;  // todo think of

    final WiresIndex.IndexPoint point = myWiresIndex.getPoint(row);
    final int pointIdx = point.getLessOrEqualWireEvent();
    final Ring<Integer> ring = point.getUsedInRing();

    for (int i = pointIdx; i < myWireEvents.size(); i++) {
      final WireEvent event = myWireEvents.get(i);
      if (event.getCommitIdx() >= row) {
        return ring;
      }
      WiresIndex.performOnRing(ring, event, myAsCommitList);
    }
    return ring;
  }

  private static class SearchWireEventsComparator implements Comparator<WireEvent> {
    private final static SearchWireEventsComparator ourInstance = new SearchWireEventsComparator();

    public static SearchWireEventsComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(WireEvent o1, WireEvent o2) {
      return new Integer(o1.getCommitIdx()).compareTo(o2.getCommitIdx());
    }
  }

  // index of item, true = item is after idx or equal
  private Pair<Integer, Boolean> getEventFor(final int row) {
    assert ! myWireEvents.isEmpty();

    final WiresIndex.IndexPoint point = myWiresIndex.getPoint(row);

    final int sizeDiff = point.getLessOrEqualWireEvent();
    final int foundIdx = Collections.binarySearch(myWireEvents.subList(point.getLessOrEqualWireEvent(), myWireEvents.size()),
                                                  new WireEvent(row, null), SearchWireEventsComparator.getInstance());
    // exact coinsidence
    if (foundIdx >= 0) return new Pair<Integer, Boolean>(sizeDiff + foundIdx, true);

    // todo check
    final int beforeInsertionIdx = (- foundIdx - 1) + sizeDiff;
    // the very first then
    if (beforeInsertionIdx < 0) return new Pair<Integer, Boolean>(0, true);
    return (beforeInsertionIdx == myWireEvents.size()) ? new Pair<Integer, Boolean>(myWireEvents.size() - 1, false) :
           new Pair<Integer, Boolean>(beforeInsertionIdx, true);
  }

  @Override
  public Iterator<WireEvent> createWireEventsIterator(final int rowInclusive) {
    if (myWireEvents.isEmpty()) return EmptyIterator.getInstance();

    final Pair<Integer, Boolean> eventFor = getEventFor(rowInclusive);
    if (! eventFor.getSecond()) return EmptyIterator.getInstance();
    return myWireEvents.subList(eventFor.getFirst(), myWireEvents.size()).iterator();
  }

  @Override
  public Commit get(int idx) {
    return myList.get(idx);
  }

  @Override
  public int getSize() {
    return myList.getSize();
  }

  public static class Commit implements Comparable<Commit>, VisibleLine {
    private final byte[] myHash;
    private int myWireNumber;
    private final long myTime;

    public Commit(final byte[] hash, final int wireNumber, long time) {
      myHash = hash;
      myWireNumber = wireNumber;
      myTime = time;
    }

    @Override
    public Object getData() {
      return this;
    }

    @Override
    public boolean isDecoration() {
      return false;
    }

    public byte[] getHash() {
      return myHash;
    }

    public long getTime() {
      return myTime;
    }

    public int getWireNumber() {
      return myWireNumber;
    }

    public void setWireNumber(int wireNumber) {
      myWireNumber = wireNumber;
    }

    @Override
    public int compareTo(Commit o) {
      final long result = myTime - o.getTime();
      return result == 0 ? 0 : (result < 0) ? -1 : 1;
    }
  }

  // commits with 1 start and end just belongs to its wire
  public static class WireEvent {
    private final int myCommitIdx;
    // wire # can be taken from commit
    @Nullable
    private final int[] myCommitsEnds;      // branch point   |/.       -1 here -> start of a wire
    @Nullable
    private int[] myWireEnds;
    private int[] myCommitsStarts;    // merge commit   |\  parents here. -1 here -> no parents, i.e. break

    public WireEvent(final int commitIdx, int[] commitsEnds) {
      myCommitIdx = commitIdx;
      myCommitsEnds = commitsEnds;
      myCommitsStarts = ArrayUtil.EMPTY_INT_ARRAY;
      myWireEnds = null;
    }

    public int getCommitIdx() {
      return myCommitIdx;
    }
    
    public void addStart(final int idx) {
      myCommitsStarts = ArrayUtil.append(myCommitsStarts, idx);
    }

    public void addWireEnd(final int idx) {
      if (myWireEnds == null) {
        myWireEnds = new int[]{idx};
      } else {
        myWireEnds = ArrayUtil.append(myWireEnds, idx);
      }
    }

    @Nullable
    public int[] getWireEnds() {
      return myWireEnds;
    }

    @Nullable
    public int[] getCommitsEnds() {
      return myCommitsEnds;
    }

    public int[] getCommitsStarts() {
      return myCommitsStarts;
    }

    // no parent commit present in quantity or exists
    public boolean isEnd() {
      return myCommitsStarts.length == 1 && myCommitsStarts[0] == -1;
    }

    public boolean isStart() {
      return myCommitsEnds != null && myCommitsEnds.length == 1 && myCommitsEnds[0] == -1;
    }
  }
}
