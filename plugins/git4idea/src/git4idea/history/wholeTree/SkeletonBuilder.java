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

import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.ReadonlyList;

import java.util.*;

/**
 * @author irengrig
 */
public class SkeletonBuilder {
  private final WireEventsListener mySkeleton;
  private final MultiMap<AbstractHash, WaitingCommit> myAwaitingParents;
  // next available idx
  private int myWireCount;
  private int rowCount;
  private final Map<Integer, Integer> mySeizedWires;

  public SkeletonBuilder(WireEventsListener treeNavigation) {
    mySkeleton = treeNavigation;

    myWireCount = 0;
    myAwaitingParents = new MultiMap<AbstractHash, WaitingCommit>();
    rowCount = 0;
    // wire number -> last commit on that wire
    mySeizedWires = new HashMap<Integer, Integer>();
  }

  public void consume(final CommitI commitI, final List<AbstractHash> parents, final ReadonlyList<CommitI> commits) {
    int wireNumber = -1;

    final Collection<WaitingCommit> awaiting = myAwaitingParents.remove(commitI.getHash());

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
        final CommitI correspCommitI = commits.get(commit.myIdx);
        commit.parentFound();

        if (commit.isMerge()) {
          // put/update start event - now we know index so can create/update wire event
          mySkeleton.addStartToEvent(commit.myIdx, rowCount);
        }

        final Integer seized = mySeizedWires.get(correspCommitI.getWireNumber());
        if (seized != null && seized == commit.myIdx) {
          if (wireNumber == -1) {
            // there is no other commits on the wire after parent -> use it
            wireNumber = correspCommitI.getWireNumber();
          }
          else {
            // if there are no other children of that commit. wire dies
            if (commit.allParentsFound()) {
              mySeizedWires.remove(correspCommitI.getWireNumber());
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
    commitI.setWireNumber(wireNumber);

    if (parents.isEmpty()) {
      // end event
      mySkeleton.wireEnds(rowCount);
      // free
      mySeizedWires.remove(wireNumber);
    } else {
      final WaitingCommit me = new WaitingCommit(rowCount, parents.size());
      for (AbstractHash parent : parents) {
        myAwaitingParents.putValue(parent, me);
      }
    }
    ++ rowCount;
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
