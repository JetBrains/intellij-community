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
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * @author irengrig
 */
public class SkeletonBuilder {
  private final TreeSkeletonImpl mySkeleton;
  private final MultiMap<String, WaitingCommit> myAwaitingParents;
  // next available idx
  private int myWireCount;
  private int rowCount;
  private final int myEventsIndexSize;
  private final Map<Integer, Integer> mySeizedWires;

  public SkeletonBuilder(final int commitArraySize, final int eventsIndexSize) {
    myEventsIndexSize = eventsIndexSize;
    mySkeleton = new TreeSkeletonImpl(commitArraySize, eventsIndexSize);

    myWireCount = 0;
    myAwaitingParents = new MultiMap<String, WaitingCommit>();
    rowCount = 0;
    // wire number -> last commit on that wire
    mySeizedWires = new HashMap<Integer, Integer>();
  }

  public void accept(final CommitHashPlusParents obj) {
    int wireNumber = -1;

    final Collection<WaitingCommit> awaiting = myAwaitingParents.remove(obj.getHash());

    if (awaiting != null) {
      // we have some children, we are a parent
      final List<WaitingCommit> awaitingList = (List<WaitingCommit>) awaiting;
      if (awaitingList.size() > 1) {
        Collections.sort(awaitingList, CommitsComparator.getInstance());
      }
      // accurately put into ends -> our children row ids
      // if any child is still last on its wire -> this wire can be used for us.
      // if child is last on its wire and we already have choosen a wire -> this child's wire should be freed
      if (awaitingList.size() > 1) {
        // merge event
        final int[] ends = new int[awaitingList.size()];
        for (int i = 0; i < awaitingList.size(); i++) {
          final WaitingCommit commit = awaitingList.get(i);
          ends[i] = commit.myIdx;
        }
        mySkeleton.addWireEvent(rowCount, ends);
      }

      for (final WaitingCommit commit : awaitingList) {
        final TreeSkeletonImpl.Commit correspCommit = mySkeleton.getCommitAt(commit.myIdx);
        commit.parentFound();

        if (commit.isMerge()) {
          // put/update start event - now we know index so can create/update wire event
          mySkeleton.addStartToEvent(commit.myIdx, rowCount);
        }

        if (mySeizedWires.get(correspCommit.getWireNumber()) == commit.myIdx) {
          if (wireNumber == -1) {
            // there is no other commits on the wire after parent -> use it
            wireNumber = correspCommit.getWireNumber();
          }
          else {
            // if there are no other children of that commit. wire dies
            if (commit.allParentsFound()) {
              mySeizedWires.remove(correspCommit.getWireNumber());
              mySkeleton.parentWireEnds(rowCount, commit.myIdx);
            }
          }
        }
      }
      // if all wires were seized, use new one
      if (wireNumber == -1) {
        wireNumber = myWireCount ++;
      }
    } else {
      // a start (head): no children. Use new wire
      wireNumber = myWireCount ++;
      // this is start
      mySkeleton.wireStarts(rowCount);
    }

    // register what we choose
    mySeizedWires.put(wireNumber, rowCount);
    mySkeleton.addCommit(rowCount, obj.getHash(), wireNumber, obj.getTime());

    if (obj.getParents().length == 0) {
      // end event
      mySkeleton.wireEnds(rowCount);
      // free
      mySeizedWires.remove(wireNumber);
    } else {
      final WaitingCommit me = new WaitingCommit(rowCount, obj.getParents().length);
      for (String parent : obj.getParents()) {
        myAwaitingParents.putValue(parent, me);
      }
    }
    ++ rowCount;
  }

  public void finished() {

    // recount of wires
    recountWires();
  }

  private void recountWires() {
    final Ring.IntegerRing ring = new Ring.IntegerRing();
    
    final Map<Integer, Integer> recalculateMap = new HashMap<Integer, Integer>();
    int runningCommitNumber = 0;  // next after previous event

    final Iterator<TreeSkeletonImpl.WireEvent> iterator = mySkeleton.createWireEventsIterator(0);
    for (; iterator.hasNext(); ) {
      final TreeSkeletonImpl.WireEvent we = iterator.next();
      for (int i = runningCommitNumber; i <= we.getCommitIdx(); i++) {
        final TreeSkeletonImpl.Commit commit = mySkeleton.getCommitAt(i);
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
        final TreeSkeletonImpl.Commit thisCommit = mySkeleton.getCommitAt(we.getCommitIdx());
        final int thisWireNum = thisCommit.getWireNumber();
        final Integer newNum = ring.getFree();
        if (newNum != thisWireNum) {
          recalculateMap.put(thisWireNum, newNum);
          // if self is start, recalculate self here
          thisCommit.setWireNumber(newNum);
        }
      }
      if (we.isEnd()) {
        ring.back(mySkeleton.getCommitAt(we.getCommitIdx()).getWireNumber());
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

    /*final Ring.IntegerRing ring = new Ring.IntegerRing();
    //todo put size into wire events, test how binary search for them helps, or index
    int runningCommitNumber = 0;  // next after previous event
    final Map<Integer, Integer> recalculateMap = new HashMap<Integer, Integer>();
    final Iterator<TreeSkeletonImpl.WireEvent> iterator = mySkeleton.createWireEventsIterator(0);
    for (; iterator.hasNext(); ) {
      final TreeSkeletonImpl.WireEvent we = iterator.next();
      // recalculate commit numbers (]
      for (int i = runningCommitNumber; i <= we.getCommitIdx(); i++) {
        // todo stopped here
        // todo stopped here
        // todo stopped here
        // todo stopped here
        // starts
      }
      //todo exclusion-inclusion etc
      // work with event
      we.setNumWiresBefore(ring.size());
      // what to return
      final int[] commitsEnds = we.getCommitsEnds();
      if (commitsEnds != null) {
        for (int end : commitsEnds) {
          final Integer replaced = recalculateMap.remove(end);
          ring.back(replaced);
        }
      }
      // recalculate
      final int[] commitsStarts = we.getCommitsStarts();
      for (int commitsStart : commitsStarts) {
        final int wire = mySkeleton.getCommitAt(commitsStart).getWireNumber();
        recalculateMap.put(wire, ring.getFree());
      }
    }  */
  }

  public TreeSkeleton getResult() {
    return mySkeleton;
  }

  // just some order
  private static class CommitsComparator implements Comparator<WaitingCommit> {
    private final static CommitsComparator ourInstance = new CommitsComparator();

    public static CommitsComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(WaitingCommit wc1, WaitingCommit wc2) {
      return new Integer(wc1.myIdx).compareTo(wc2.myIdx);
    }
  }

  private static class WaitingCommit {
    private final int myIdx;
    private int myNumParents; // i.e. a start
    private final boolean myIsMerge;

    private WaitingCommit(int idx, int numParents) {
      myIdx = idx;
      myNumParents = numParents;
      myIsMerge = myNumParents > 1;
    }

    public boolean isMerge() {
      return myIsMerge;
    }

    public void parentFound() {
      -- myNumParents;
    }

    public boolean allParentsFound() {
      return myNumParents == 0;
    }
  }
}
