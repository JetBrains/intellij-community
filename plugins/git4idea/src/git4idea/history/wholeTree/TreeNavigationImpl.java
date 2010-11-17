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

import com.intellij.openapi.vcs.Ring;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ReadonlyList;

import java.util.*;

/**
 * @author irengrig
 */
public class TreeNavigationImpl implements TreeNavigation, WireEventsListener {
  public static final int[] MARKER = new int[]{-1};
  private final TreeMap<Integer, WireEvent> myWireEvents;
  // created for some of wire events; idx of _commit_
  private final TreeMap<Integer, RingIndex> myRingIndex;
  private final int myCommitIndexInterval;
  private final int myNumWiresInGroup;

  public TreeNavigationImpl(final int commitIndexInterval, final int numWiresInGroup) {
    myCommitIndexInterval = commitIndexInterval;
    myNumWiresInGroup = numWiresInGroup;
    myWireEvents = new TreeMap<Integer, WireEvent>();
    myRingIndex = new TreeMap<Integer, RingIndex>();
  }

  public void recalcIndex(final ReadonlyList<CommitI> commits) {
    if (myWireEvents.isEmpty()) return;

    Integer lastIndexKey = myRingIndex.isEmpty() ? null : myRingIndex.lastKey();
    final SortedMap<Integer, WireEvent> tail;
    final Ring<Integer> ring;
    if (lastIndexKey != null) {
      tail = myWireEvents.tailMap(lastIndexKey, false);
      final int size = tail.size();
      if (size == 1) return;
      final Integer lastKey = myWireEvents.lastKey();
      if (lastKey - lastIndexKey < myCommitIndexInterval || size < myNumWiresInGroup) {
        return;
      }
      ring = myRingIndex.lastEntry().getValue().getUsedInRing();
    } else {
      lastIndexKey = myWireEvents.firstKey();
      ring = new Ring.IntegerRing();
      final List<Integer> used = ring.getUsed();
      myRingIndex.put(lastIndexKey, new RingIndex(used.toArray(new Integer[used.size()])));
      performOnRing(ring, myWireEvents.firstEntry().getValue(), commits);
      tail = myWireEvents.tailMap(lastIndexKey, false);
    }

    int cnt = 0;
    int recordCommitIdx = myCommitIndexInterval + myWireEvents.floorKey(lastIndexKey);
    for (Integer integer : tail.keySet()) {
      ++ cnt;
      if ((cnt >= myNumWiresInGroup) || (integer >= recordCommitIdx)) {
        final List<Integer> used = ring.getUsed();
        myRingIndex.put(integer, new RingIndex(used.toArray(new Integer[used.size()])));
        cnt = 0;
        recordCommitIdx += myCommitIndexInterval;
      }
      performOnRing(ring, tail.get(integer), commits);
    }
  }

  private static void performOnRing(final Ring<Integer> ring, final WireEvent event, final ReadonlyList<CommitI> convertor) {
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

  @Override
  public Iterator<WireEvent> createWireEventsIterator(int rowInclusive) {
    return myWireEvents.tailMap(rowInclusive).values().iterator();
  }

  @Override
  public Ring<Integer> getUsedWires(int row, ReadonlyList<CommitI> commits) {
    final Map.Entry<Integer, RingIndex> entry = myRingIndex.floorEntry(row);
      if (entry == null) return new Ring.IntegerRing();
    final Ring<Integer> ring = entry.getValue().getUsedInRing();
      if (entry.getKey() == row) {
        return ring;
      }
      final Iterator<WireEvent> iterator = createWireEventsIterator(entry.getKey());
      while (iterator.hasNext()) {
        final WireEvent event = iterator.next();
        if (event.getCommitIdx() >= row) {
          return ring;
        }
        performOnRing(ring, event, commits);
      }
    return ring;
  }

  @Override
  public void addStartToEvent(int row, final int parentRow) {
    modify(row, new Consumer<WireEvent>() {
      @Override
      public void consume(WireEvent wireEvent) {
        wireEvent.addStart(parentRow);
      }
    });
  }

  @Override
  public void wireStarts(int row) {
    myWireEvents.put(row, new WireEvent(row, MARKER));
  }

  @Override
  public void wireEnds(int row) {
    modify(row, new Consumer<WireEvent>() {
      @Override
      public void consume(WireEvent wireEvent) {
        wireEvent.addStart(-1);
      }
    });
  }

  @Override
  public void addWireEvent(int row, int[] branched) {
    final WireEvent wireEvent = new WireEvent(row, branched);
    myWireEvents.put(row, wireEvent);
  }

  @Override
  public void parentWireEnds(int row, final int parentRow) {
    modify(row, new Consumer<WireEvent>() {
      @Override
      public void consume(WireEvent wireEvent) {
        wireEvent.addWireEnd(parentRow);
      }
    });
  }

  private void modify(final int row, final Consumer<WireEvent> consumer) {
    WireEvent event = myWireEvents.get(row);
    if (event == null) {
      event = new WireEvent(row, null);
      myWireEvents.put(row, event);
    }
    consumer.consume(event);
  }

  private static class RingIndex {
    private final Integer[] myWireNumbers;

    private RingIndex(Integer[] wireNumbers) {
      myWireNumbers = wireNumbers;
    }

    public Ring<Integer> getUsedInRing() {
      return new Ring.IntegerRing(Arrays.<Integer>asList(myWireNumbers));
    }
  }

  public void recountWires(final int fromIdx, final ReadonlyList<CommitI> commits) {
    final Ring.IntegerRing ring = new Ring.IntegerRing();

    final Map<Integer, Integer> recalculateMap = new HashMap<Integer, Integer>();
    int runningCommitNumber = 0;  // next after previous event

    final Iterator<WireEvent> iterator = createWireEventsIterator(fromIdx);
    for (; iterator.hasNext(); ) {
      final WireEvent we = iterator.next();
      for (int i = runningCommitNumber; i <= we.getCommitIdx(); i++) {
        final CommitI commit = commits.get(i);
        final Integer newWire = recalculateMap.get(commit.getWireNumber());
        if (newWire != null) {
          commit.setWireNumber(newWire);
        }
      }
      runningCommitNumber = we.getCommitIdx() + 1;

      final int[] wireEnds = we.getWireEnds();
      if (wireEnds != null) {
        for (int wireEnd : wireEnds) {
          ring.back(wireEnd);
        }
      }
      if (we.isStart()) {
        final CommitI thisCommit = commits.get(we.getCommitIdx());
        final int thisWireNum = thisCommit.getWireNumber();
        final Integer newNum = ring.getFree();
        if (newNum != thisWireNum) {
          recalculateMap.put(thisWireNum, newNum);
          // if self is start, recalculate self here
          thisCommit.setWireNumber(newNum);
        }
      }
      if (we.isEnd()) {
        ring.back(commits.get(we.getCommitIdx()).getWireNumber());
      }
      final int[] commitsStarts = we.getCommitsStarts();
      if (commitsStarts.length > 0 && (! we.isEnd())) {
        for (int commitStart : commitsStarts) {
          Integer corrected = recalculateMap.get(commitStart);
          corrected = (corrected == null) ? commitStart : corrected;
          if (! ring.isNumUsed(corrected)) {
            final Integer newNum = ring.getFree();
            recalculateMap.put(commitStart, newNum);
          }
        }
      }
    }
  }
}
