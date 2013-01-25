/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/26/11
 * Time: 4:08 PM
 */
public class TreeHighlighter {
  private AbstractHash myPoint;
  private final VirtualFile myRoot;
  private int myInitIdx;
  private int myIdx;
  // self is here
  private final Set<AbstractHash> myParents;

  private final BigTableTableModel myModel;
  private final Set<Integer> myIncludedWires;
  private final boolean mySingleRoot;
  private boolean myInitialized;

  private final static int ourIndexInterval = 200;
  private final TreeMap<Integer, Set<Integer>> myIndexOfGrey;
  private final TreeMap<Integer, Set<Integer>> myIndexOfGreyNotReady;
  private boolean myDumb;

  public TreeHighlighter(BigTableTableModel model, VirtualFile root, final int idx) {
    myModel = model;
    myDumb = false;
    myRoot = root;
    myInitIdx = idx;
    myInitialized = false;
    myIndexOfGrey = new TreeMap<Integer, Set<Integer>>();
    myIndexOfGreyNotReady = new TreeMap<Integer, Set<Integer>>();

    myParents = new HashSet<AbstractHash>();
    myIncludedWires = new HashSet<Integer>();
    mySingleRoot = myModel.getRootsHolder().getRoots().size() == 1;
  }

  public void setPoint(AbstractHash point) {
    myDumb = false;
    myPoint = point;
    myParents.clear();
    myIndexOfGrey.clear();
    myIndexOfGreyNotReady.clear();
    myInitIdx = -1;
    myIdx = -1;
    myIncludedWires.clear();
    myInitialized = false;
  }

  public boolean isIncluded(final AbstractHash idx) {
    return myDumb ? true : myParents.contains(idx);
  }

  private boolean init() {
    CommitI commitAt = null;
    if (myInitIdx == -1) {
      for (int i = 0; i < myModel.getRowCount(); i++) {
        commitAt = myModel.getCommitAt(i);
        if (! commitAt.holdsDecoration() && AbstractHash.hashesEqual(commitAt.getHash(), myPoint)) {
          myInitIdx = i;
          break;
        }
      }
      if (myInitIdx == -1) return false;
    }
    if (commitAt == null) {
      commitAt = myModel.getCommitAt(myInitIdx);
    }
    myIdx = myInitIdx;
    myIncludedWires.add(commitAt.getWireNumber());
    return true;
  }
  
  public Map<Integer, Set<Integer>> getGreyForInterval(final int from, final int to, int repoCorrection, final Set<Integer> wireModificationSet) {
    assert from < to;
    if (myDumb) return null;

    final Set<Integer> firstUsed;
    final WireEventsIterator eventsIterator;

    int idxPoint = -1;
    if (from < myInitIdx) {
      final Map<VirtualFile, WireEventsIterator> groupIterators = myModel.getAllGroupIterators(from);
      eventsIterator = groupIterators.get(myRoot);

      Map.Entry<Integer, Set<Integer>> entry = myIndexOfGreyNotReady.floorEntry(eventsIterator.getFloor());
      firstUsed = new HashSet<Integer>();
      if (entry == null) {
        entry = myIndexOfGreyNotReady.firstEntry();
      } else {
        firstUsed.addAll(entry.getValue());
      }
      if (entry == null) return Collections.emptyMap();
      idxPoint = entry.getKey();
    } else {
      Map.Entry<Integer, Set<Integer>> entry = myIndexOfGreyNotReady.floorEntry(from);
      if (entry == null) {
        entry = myIndexOfGreyNotReady.firstEntry();
      }
      if (entry == null) return Collections.emptyMap();
      idxPoint = entry.getKey();
      firstUsed = new HashSet<Integer>(entry.getValue());
      final Map<VirtualFile, WireEventsIterator> groupIterators = myModel.getAllGroupIterators(entry.getKey());
      eventsIterator = groupIterators.get(myRoot);
    }

    if (eventsIterator.getFloor() > to) {
      return Collections.emptyMap();
    }
    final Iterator<WireEventI> wireEventsIterator = eventsIterator.getWireEventsIterator();

    wireModificationSet.add(myInitIdx);
    int previous = idxPoint;
    if (previous == myInitIdx && from > previous) {
      firstUsed.add(myModel.getCommitAt(myInitIdx).getWireNumber());
    }
    WireEventI event = null;
    while (wireEventsIterator.hasNext()) {
      event = wireEventsIterator.next();
      if (event.getCommitIdx() >= from) break;
      if (myInitIdx > previous && myInitIdx <= event.getCommitIdx()) {
        firstUsed.add(myModel.getCommitAt(myInitIdx).getWireNumber());
      }
      applyEventToGrey(firstUsed, event, wireModificationSet);
      previous = event.getCommitIdx();
      event = null;
    }

    int idx = from;
    final HashMap<Integer, Set<Integer>> result = new HashMap<Integer, Set<Integer>>();
    boolean nextToExit = false;
    while (wireEventsIterator.hasNext() && ! nextToExit) {
      if (event == null) {
        event = wireEventsIterator.next();
      }
      if (event.getCommitIdx() > to) {
        nextToExit = true;
      }

      // including event idx
      HashSet<Integer> value = copyOfUsed(repoCorrection, firstUsed);
      for (int i = idx; i <= event.getCommitIdx(); i++) {
        value = putForIdx(repoCorrection, wireModificationSet, firstUsed, result, value, i);
      }
      final CommitI eventCommit = myModel.getCommitAt(event.getCommitIdx());
      if (myParents.contains(eventCommit.getHash())) {
        final Set<Integer> integers1 = result.get(event.getCommitIdx());
        if (integers1 != null && ! integers1.contains(eventCommit.getWireNumber() + repoCorrection)) {
          wireModificationSet.add(event.getCommitIdx());
          final Set<Integer> integers = new HashSet<Integer>(integers1);
          integers.add(eventCommit.getWireNumber() + repoCorrection);
          result.put(event.getCommitIdx(), integers);
        }
      }
      applyEventToGrey(firstUsed, event, wireModificationSet);

      idx = event.getCommitIdx() + 1;
      event = null;
    }
    
    HashSet<Integer> value = copyOfUsed(repoCorrection, firstUsed);
    for (int i = idx; i < to; i++) {
      value = putForIdx(repoCorrection, wireModificationSet, firstUsed, result, value, i);
    }

    return result;
  }

  private HashSet<Integer> putForIdx(int repoCorrection,
                                     Set<Integer> wireModificationSet,
                                     Set<Integer> firstUsed,
                                     HashMap<Integer, Set<Integer>> result, HashSet<Integer> value, int i) {
    if (i == myInitIdx) {
      final int wireNumber = myModel.getCommitAt(myInitIdx).getWireNumber();
      if (! value.contains(wireNumber + repoCorrection)) {
        wireModificationSet.add(myInitIdx);
        firstUsed.add(wireNumber);
        value = new HashSet<Integer>();
        for (Integer integer : firstUsed) {
          value.add(integer + repoCorrection);
        }
      }
    }
    result.put(i, value);
    return value;
  }

  private HashSet<Integer> copyOfUsed(int repoCorrection, Set<Integer> firstUsed) {
    HashSet<Integer> value;
    value = new HashSet<Integer>();
    for (Integer integer : firstUsed) {
      value.add(integer + repoCorrection);
    }
    return value;
  }

  private void recalcIndex() {
    if (myIdx == -1) return;
    Integer lastKey = myIndexOfGrey.isEmpty() ? null : myIndexOfGrey.lastKey();

    final WireEventsIterator eventsIterator;
    final Set<Integer> firstUsed;

    if (lastKey == null) {
      final CommitI initCommit = myModel.getCommitAt(myInitIdx);
      final Map<VirtualFile, WireEventsIterator> groupIterators = myModel.getAllGroupIterators(myInitIdx);
      eventsIterator = groupIterators.get(myRoot);

      firstUsed = new HashSet<Integer>();
      final Integer floor = eventsIterator.getFloor();
      if (floor == null) return;
      // lets do it for floor
      //assert floor <= myInitIdx;
      /*if (floor.intValue() == myInitIdx) {
        firstUsed.remove(initCommit.getWireNumber());
      }*/
      myIndexOfGrey.put(floor, new HashSet<Integer>(firstUsed));
      lastKey = floor;
    } else {
      if (lastKey.intValue() + ourIndexInterval > myModel.getRowCount()) return;

      final Map<VirtualFile, WireEventsIterator> groupIterators = myModel.getAllGroupIterators(lastKey);
      eventsIterator = groupIterators.get(myRoot);
      // index shows before-state
      firstUsed = new HashSet<Integer>(myIndexOfGrey.get(lastKey));
    }

    final Iterator<WireEventI> iterator = eventsIterator.getWireEventsIterator();
    myIndexOfGreyNotReady.clear();

    boolean isCompletePart = true;
    while (iterator.hasNext()) {
      final WireEventI event = iterator.next();
      if (event.getCommitIdx() >= (ourIndexInterval + lastKey)) {
        if (isCompletePart) {
          myIndexOfGrey.put(event.getCommitIdx(), new HashSet<Integer>(firstUsed));
        } else {
          myIndexOfGreyNotReady.put(event.getCommitIdx(), new HashSet<Integer>(firstUsed));
        }
        lastKey = event.getCommitIdx();
      }
      applyEventToGrey(firstUsed, event, null);

      isCompletePart = isCompletePart && event.getWaitStartsNumber() == 0;
    }
    myIndexOfGreyNotReady.putAll(myIndexOfGrey);
  }

  private void applyEventToGrey(Set<Integer> firstUsed, WireEventI event, Set<Integer> wireModificationSet) {
    final CommitI eventCommit = myModel.getCommitAt(event.getCommitIdx());
    if (event.getCommitIdx() == myInitIdx && ! firstUsed.contains(eventCommit.getWireNumber())) {
      firstUsed.add(eventCommit.getWireNumber());
      if (wireModificationSet != null) {
        wireModificationSet.add(myInitIdx);
      }
    }
    final int[] wireEnds = event.getWireEnds();
    boolean anyWireIncluded = false;
    if (wireEnds != null) {
      for (int wireEnd : wireEnds) {
        if (wireEnd != -1) {
          final int wireNumber = myModel.getCommitAt(wireEnd).getWireNumber();
          anyWireIncluded |= firstUsed.remove(wireNumber);
        }
      }
    }
    if (anyWireIncluded) {
      if (! firstUsed.contains(eventCommit.getWireNumber())) {
        if (wireModificationSet != null) {
          wireModificationSet.add(event.getCommitIdx());
        }
      }
      firstUsed.add(eventCommit.getWireNumber());
    }
    final int[] commitsStarts = event.getCommitsStarts();
    final int[] futureWireStarts = event.getFutureWireStarts();
    if (myParents.contains(eventCommit.getHash())) {
      if (commitsStarts != null) {
        for (int commitsStart : commitsStarts) {
          if (commitsStart == -1) continue;
          final CommitI commitAt = myModel.getCommitAt(commitsStart);
          firstUsed.add(commitAt.getWireNumber());
        }
      }
      if (futureWireStarts != null && futureWireStarts.length > 0) {
        for (int futureWireStart : futureWireStarts) {
          firstUsed.add(futureWireStart);
        }
      }
    }
  }

  public void update(int recountFrom) {
    if (recountFrom <= myInitIdx) {
      reset();
    } else {
      final NavigableMap<Integer, Set<Integer>> tailIdx = myIndexOfGrey.tailMap(recountFrom, true);
      final Iterator<Integer> iterator = tailIdx.keySet().iterator();
      while (iterator.hasNext()) {
        Integer next = iterator.next();
        iterator.remove();
      }
      //
      final NavigableMap<Integer, Set<Integer>> tailNotReadyIdx = myIndexOfGreyNotReady.tailMap(recountFrom, true);
      final Iterator<Integer> iterator1 = tailNotReadyIdx.keySet().iterator();
      while (iterator1.hasNext()) {
        Integer next = iterator1.next();
        iterator1.remove();
      }
    }
    if (myPoint == null) return;
    if (! myInitialized) {
      myInitialized = init();
      if (! myInitialized) return;
    }
    final Map<VirtualFile,WireEventsIterator> groupIterators = myModel.getAllGroupIterators(myIdx);
    final WireEventsIterator eventsIterator = groupIterators.get(myRoot);

    Iterator<WireEventI> iterator2 = eventsIterator.getWireEventsIterator();

    int runningIdx = myIdx;
    final Set<Integer> includedWires = new HashSet<Integer>();
    includedWires.addAll(myIncludedWires);
    myIncludedWires.clear();

    int lastEventIdx = myIdx;
    while (iterator2.hasNext() && ! includedWires.isEmpty()) {
      final WireEventI event = iterator2.next();
      lastEventIdx = event.getCommitIdx();
      final Set<Integer> ends = new HashSet<Integer>();
      final int self = myModel.getCommitAt(event.getCommitIdx()).getWireNumber();
      ends.add(self);
      final int[] wireEnds = event.getWireEnds();
      if (wireEnds != null) {
        for (int wireEnd : wireEnds) {
          if (wireEnd == -1) continue;
          ends.add(myModel.getCommitAt(wireEnd).getWireNumber());
        }
      }
      boolean containsAny = false;
      for (Integer end : ends) {
        if (includedWires.contains(end)) {
          containsAny = true;
          break;
        }
      }
      if (! containsAny) continue;
      
      fillCommits(runningIdx, event.getCommitIdx(), includedWires);

      if (event.getWaitStartsNumber() > 0 && myIncludedWires.isEmpty()) {
        myIncludedWires.addAll(includedWires);
      }

      includedWires.removeAll(ends);
      includedWires.add(self);
      final int[] commitsStarts = event.getCommitsStarts();
      if (commitsStarts != null) {
        for (int commitsStart : commitsStarts) {
          if (commitsStart == -1) continue;
          includedWires.add(myModel.getCommitAt(commitsStart).getWireNumber());
        }
      }
      runningIdx = event.getCommitIdx();
      if (event.getWaitStartsNumber() == 0 && myIncludedWires.isEmpty()) {
        myIdx = event.getCommitIdx();
      }
    }

    final int lastForRoot = myModel.getLastForRoot(myRoot);
    if (runningIdx < lastForRoot) {
      fillCommits(runningIdx, lastForRoot, includedWires);
    }

    if (myIncludedWires.isEmpty()) {
      myIncludedWires.addAll(includedWires);
    }

    recalcIndex();
  }

  private void fillCommits(int curIdx, int commitIdx, Set<Integer> includedWires) {
    for (int i = curIdx; i <= commitIdx; i++) {
      final CommitI commitAt = myModel.getCommitAt(i);
      if ((mySingleRoot || commitAt.selectRepository(myModel.getRootsHolder().getRoots()).equals(myRoot))
          && includedWires.contains(commitAt.getWireNumber())) {
        myParents.add(commitAt.getHash());
      }
    }
  }

  public void reset() {
    myIncludedWires.clear();
    myIdx = -1;
    myInitIdx = -1;
    myInitialized = false;
  }

  public void setDumb() {
    myDumb = true;
  }

  public boolean isDumb() {
    return myDumb;
  }

  public AbstractHash getPoint() {
    return myPoint;
  }
}
