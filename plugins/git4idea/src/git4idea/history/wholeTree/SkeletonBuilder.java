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

import com.intellij.openapi.vcs.Ring;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.ReadonlyList;

import java.util.*;

/**
 * @author irengrig
 */
public class SkeletonBuilder {
  private final WireEventsListener mySkeleton;
  private final MultiMap<AbstractHash, WaitingItem> myAwaitingParents;
  private final MultiMap<Integer, WaitingItem> myBackIndex;
  // next available idx
  private final Ring.IntegerRing myRing;
  private final BidirectionalMap<Integer, Integer> mySeizedWires;
  // !! can intersect with existing, but own lifecycle
  private final BidirectionalMap<Integer, AbstractHash> myFutureSeizedWires;
  private final Convertor<Integer,List<Integer>> myFutureConvertor;
  private int myMaxWireNum;

  public SkeletonBuilder(WireEventsListener treeNavigation) {
    mySkeleton = treeNavigation;

    myAwaitingParents = new MultiMap<AbstractHash, WaitingItem>();
    myBackIndex = new MultiMap<Integer, WaitingItem>();
    myRing = new Ring.IntegerRing();
    // wire number -> last commit on that wire
    mySeizedWires = new BidirectionalMap<Integer, Integer>();
    myFutureSeizedWires = new BidirectionalMap<Integer, AbstractHash>();
    myFutureConvertor = new Convertor<Integer, List<Integer>>() {
      @Override
      public List<Integer> convert(Integer o) {
        return getFutureWireStarts(o);
      }
    };
    myMaxWireNum = 0;
  }

  public void consume(final CommitI commitI, final List<AbstractHash> parents, final ReadonlyList<CommitI> commits, final int rowCount) {
    int wireNumber = -1;

    // will become real!
    // todo: superflous information both in waiting item and in future map
    myFutureSeizedWires.removeValue(commitI.getHash());
    final Collection<WaitingItem> awaiting = myAwaitingParents.remove(commitI.getHash());

    if (awaiting != null) {
      final List<WaitingItem> awaitingList = (List<WaitingItem>) awaiting;
      if (awaitingList.size() > 1) {
        Collections.sort(awaitingList, CommitsComparator.getInstance());
      }

      final List<WaitingItem> willReturnTheirWires = new SmartList<WaitingItem>();
      for (final WaitingItem waiting : awaitingList) {
        Collection<WaitingItem> waitingCommits = myBackIndex.get(waiting.myIdx);
        waitingCommits.remove(waiting);
        if (waitingCommits.isEmpty()) {
          myBackIndex.remove(waiting.myIdx);
        }

        if (wireNumber == -1) {
          wireNumber = waiting.getWire();
        } else {
          assert wireNumber == waiting.getWire();
        }

        final CommitI waitingI = commits.get(waiting.myIdx);

        if (waiting.isMerge()) {
          // put/update start event - now we know index so can create/update wire event
          mySkeleton.addStartToEvent(waiting.myIdx, rowCount, waiting.getWire());
        }

        final Integer seized = mySeizedWires.get(waitingI.getWireNumber());
        AbstractHash something = myFutureSeizedWires.get(waitingI.getWireNumber());
        if (seized != null && seized == waiting.myIdx && waitingI.getWireNumber() != wireNumber && something == null) {
          // return
          willReturnTheirWires.add(waiting);
        }
      }

      for (WaitingItem waitingItem : willReturnTheirWires) {
        final CommitI waitingI = commits.get(waitingItem.myIdx);
        myRing.back(waitingI.getWireNumber());
        mySeizedWires.remove(waitingI.getWireNumber());
        mySkeleton.parentWireEnds(rowCount, waitingItem.myIdx);
      }

      // event about branch!
      if (awaitingList.size() > 1) {
        // merge event
        final int[] ends = new int[awaitingList.size()];
        for (int i = 0; i < awaitingList.size(); i++) {
          final WaitingItem waiting = awaitingList.get(i);
          ends[i] = waiting.myIdx;
        }
        mySkeleton.setEnds(rowCount, ends);
      }
    } else {
      // a start (head): no children. Use new wire
      wireNumber = myRing.getFree();
      // this is start
      mySkeleton.wireStarts(rowCount);
      mySkeleton.setEnds(rowCount, new int[] {-1});
    }

    // register what we choose
    mySeizedWires.put(wireNumber, rowCount);
    commitI.setWireNumber(wireNumber);

    myMaxWireNum = Math.max(myMaxWireNum, myRing.getMaxNumber());
    if (parents.isEmpty()) {
      // end event
      mySkeleton.wireEnds(rowCount);
      // free
      myRing.back(wireNumber);
      mySeizedWires.remove(wireNumber);
    } else {
      boolean selfUsed = false;
      final List<Integer> parentWires = new ArrayList<Integer>();
      for (AbstractHash parent : parents) {
        WaitingItem item;
        Collection<WaitingItem> existing = myAwaitingParents.get(parent);
        if (existing != null && ! existing.isEmpty()) {
          // use its wire!
          final int wire = existing.iterator().next().getWire();
          item = new WaitingItem(rowCount, wire, parents.size() > 1);
          parentWires.add(wire);
        } else {
          // a start (head): no children. Use new wire
          Integer parentWire;
          if (! selfUsed) {
            parentWire = wireNumber;
            selfUsed = true;
          }
          else {
            // this is start
            parentWire = myRing.getFree();
            mySkeleton.wireStarts(rowCount);
          }
          parentWires.add(parentWire);
          myFutureSeizedWires.put(parentWire, parent);
          item = new WaitingItem(rowCount, parentWire, parents.size() > 1);
        }
        myAwaitingParents.putValue(parent, item);
        myBackIndex.putValue(item.myIdx, item);
      }
      if (parents.size() > 1) {
        mySkeleton.setWireStartsNumber(rowCount, parentWires.toArray(new Integer[parentWires.size()]));
      }
    }
  }

  public int getMaxWireNum() {
    return myMaxWireNum;
  }

  // just some order
  private static class CommitsComparator implements Comparator<WaitingItem> {
    private final static CommitsComparator ourInstance = new CommitsComparator();

    public static CommitsComparator getInstance() {
      return ourInstance;
    }
    
    @Override
    public int compare(WaitingItem wc1, WaitingItem wc2) {
      return new Integer(wc1.getWire()).compareTo(wc2.getWire());
    }
  }
  
  private static class WaitingItem {
    private int myIdx;
    private int myWire;
    private boolean myIsMerge;

    private WaitingItem(int idx, int wire, boolean isMerge) {
      myIdx = idx;
      myWire = wire;
      myIsMerge = isMerge;
    }

    public boolean isMerge() {
      return myIsMerge;
    }

    public int getIdx() {
      return myIdx;
    }

    public int getWire() {
      return myWire;
    }
  }

  public List<Integer> getFutureWireStarts(final int idx) {
    Collection<WaitingItem> waitingItems = myBackIndex.get(idx);
    if (waitingItems == null || waitingItems.isEmpty()) return Collections.emptyList();
    final List<Integer> result = new ArrayList<Integer>();
    for (WaitingItem item : waitingItems) {
      result.add(item.getWire());
    }
    return result;
  }

  public Convertor<Integer, List<Integer>> getFutureConvertor() {
    return myFutureConvertor;
  }
}
