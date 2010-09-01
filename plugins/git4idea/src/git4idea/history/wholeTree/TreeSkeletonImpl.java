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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.EmptyIterator;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author irengrig
 */
public class TreeSkeletonImpl implements TreeSkeleton {
  private final int myPack;
  private final int myWireEventsPack;

  private final int mySize2Power;
  private final int myWireEventsIdxSize2Power;

  // just commit hashes
  private final List<Commit[]> myList;

  // hierarchy structure events
  private final List<WireEvent> myWireEvents;

  // Pair<WireEvent, WireEvent> -> before event, after event todo build afterwards!!
  private final List<Pair<Integer, Integer>> myEventsIdx;
  private int mySize;

  public TreeSkeletonImpl(final int size2Power, final int wireEventsIdxSize2Power) {
    mySize2Power = size2Power;
    myWireEventsIdxSize2Power = wireEventsIdxSize2Power;

    // some defense. small values for tests
    assert (size2Power < 16) && (wireEventsIdxSize2Power < 16) && (size2Power > 1) && (wireEventsIdxSize2Power > 1);

    myPack = (int) Math.pow(2, size2Power);
    myWireEventsPack = (int) Math.pow(2, wireEventsIdxSize2Power);

    myList = new LinkedList<Commit[]>();
    myWireEvents = new LinkedList<WireEvent>(); // todo can use another structure, a list of arrays?
    myEventsIdx = new LinkedList<Pair<Integer, Integer>>();
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

  public void addWireEvent(final int row, final int[] branched) {
    final WireEvent wireEvent = new WireEvent(row, branched);
    myWireEvents.add(wireEvent);

    // todo create idx afterwards
    /*final int idx = wireEvent.getCommitIdx() >> myWireEventsIdxSize2Power;
    if (idx < myEventsIdx.size()) return;

    // when idx of index is event index, put same objects as previous and next
    final int currentIdx = myWireEvents.size() - 1;
    final int previous = (wireEvent.getCommitIdx() ^ (idx << myWireEventsIdxSize2Power)) == 0 ? currentIdx : (currentIdx - 1);
    final Pair<Integer, Integer> indexEntry = new Pair<Integer, Integer>(previous, currentIdx);
    while (idx >= myEventsIdx.size()) {
      myEventsIdx.add(indexEntry);
    }*/
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
      myWireEvents.add(- foundIdx - 1, event);
    }
    wireEventConsumer.consume(event);
  }

  public void markBreaks(final Collection<Integer> breaks) {
    for (Integer aBreak : breaks) {
      modifyWireEvent(aBreak, new Consumer<WireEvent>() {
        @Override
        public void consume(WireEvent wireEvent) {
          wireEvent.addStart(-1);
        }
      });
    }
  }
  
  // todo not very well
  public void commitsAdded(final int rowThatWasLast) {
    mySize = rowThatWasLast + 1;
    final int itemNumber = rowThatWasLast >> mySize2Power;
    final int size = (rowThatWasLast ^ (itemNumber << 10)) + 1;
    final Commit[] newArr = new Commit[size];
    System.arraycopy(myList.get(itemNumber), 0, newArr, 0, size);
    myList.set(itemNumber, newArr);
  }

  public void addCommit(final int row, final String hash, final int wireNumber, final long time) {
    final int itemNumber = row >> mySize2Power;

    final Commit[] commits;
    if (itemNumber >= myList.size()) {
      commits = new Commit[myPack];
      myList.add(itemNumber, commits);
    } else {
      commits = myList.get(itemNumber);
    }
    commits[row ^ (itemNumber << mySize2Power)] = new Commit(hash.getBytes(), wireNumber, time);
  }

  @Override
  public short getNumberOfWiresOnEnter(final int row) {
    if (myWireEvents.isEmpty()) return -1;  // todo think of

    final Pair<Integer, Boolean> eventFor = getEventFor(row);
    // TODO
    // TODO
    // TODO
    // TODO
    /*return eventFor.getSecond() ? myWireEvents.get(eventFor.getFirst()).getTotalLinesNumberBefore() :
           myWireEvents.get(eventFor.getFirst()).getTotalLinesNumberAfter();*/
    return -1;
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

    final int idx = row >> myWireEventsIdxSize2Power;
    int eventsIdx = 0;
    if (! myEventsIdx.isEmpty()) {
      final Pair<Integer, Integer> pair = myEventsIdx.get(idx);
      eventsIdx = pair.getFirst();
    }

    final int foundIdx = Collections.binarySearch(myWireEvents, new WireEvent(row, null), SearchWireEventsComparator.getInstance());
    // exact coinsidence
    if (foundIdx >= 0) return new Pair<Integer, Boolean>(foundIdx, true);

    // todo check
    final int beforeInsertionIdx = - foundIdx - 2;
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

  // todo test
  @Override
  public Commit getCommitAt(final int row) {
    final int itemNumber = row >> mySize2Power;
    return myList.get(itemNumber)[row ^ (itemNumber << mySize2Power)];
  }

  public static class Commit {
    private final byte[] myHash;
    private int myWireNumber;
    private final long myTime;

    public Commit(final byte[] hash, final int wireNumber, long time) {
      myHash = hash;
      myWireNumber = wireNumber;
      myTime = time;
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
  }

  // commits with 1 start and end just belongs to its wire
  public static class WireEvent {
    private final int myCommitIdx;
    private int myNumWiresBefore;
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

    /*public short getTotalLinesNumberAfter() {
      return (short) (myTotalLinesNumberBefore + (myWiresStarts == null ? 0 : myWiresStarts.length)
                   - (myWiresEnds == null ? 0 : myWiresEnds.length));
    }*/

    public int getNumWiresBefore() {
      return myNumWiresBefore;
    }

    public void setNumWiresBefore(int numWiresBefore) {
      myNumWiresBefore = numWiresBefore;
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
