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
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.PatchLine;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BeforeAfter;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/27/11
 * Time: 3:59 PM
 */
public class GenericPatchApplier {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier");
  private final static int ourMaxWalk = 1000;
  
  private final TreeMap<TextRange, MyAppliedData> myTransformations;
  private final List<String> myLines;
  private final List<PatchHunk> myHunks;
  private boolean myHadAlreadyAppliedMet;

  private final ArrayList<SplitHunk> myNotBound;
  private final ArrayList<SplitHunk> myNotExact;
  private boolean mySuppressNewLineInEnd;
  
  private void debug(final String s) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(s);
    }
  }

  public GenericPatchApplier(final CharSequence text, List<PatchHunk> hunks) {
    debug("GenericPatchApplier created, hunks: " + hunks.size());
    myLines = new ArrayList<String>();
    Collections.addAll(myLines, LineTokenizer.tokenize(text, false));
    myHunks = hunks;
    myTransformations = new TreeMap<TextRange, MyAppliedData>(new Comparator<TextRange>() {
      @Override
      public int compare(TextRange o1, TextRange o2) {
        return new Integer(o1.getStartOffset()).compareTo(new Integer(o2.getStartOffset()));
      }
    });
    myNotExact = new ArrayList<SplitHunk>();
    myNotBound = new ArrayList<SplitHunk>();
  }

  public ApplyPatchStatus getStatus() {
    if (! myNotExact.isEmpty()) {
      return ApplyPatchStatus.FAILURE;
    } else {
      if (myTransformations.isEmpty() && myHadAlreadyAppliedMet) return ApplyPatchStatus.ALREADY_APPLIED;
      boolean haveAlreadyApplied = myHadAlreadyAppliedMet;
      boolean haveTrue = false;
      for (MyAppliedData data : myTransformations.values()) {
        if (data.isHaveAlreadyApplied()) {
          haveAlreadyApplied |= true;
        } else {
          haveTrue = true;
        }
      }
      if (haveAlreadyApplied && ! haveTrue) return ApplyPatchStatus.ALREADY_APPLIED;
      if (haveAlreadyApplied) return ApplyPatchStatus.PARTIAL;
      return ApplyPatchStatus.SUCCESS;
    }
  }

  private void printTransformations(final String comment) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(comment + " GenericPatchApplier.printTransformations ---->");
      int cnt = 0;
      for (Map.Entry<TextRange, MyAppliedData> entry : myTransformations.entrySet()) {
        final TextRange key = entry.getKey();
        final MyAppliedData value = entry.getValue();
        LOG.info(String.valueOf(cnt) +
                 " lines " +
                 key.getStartOffset() +
                 ":" +
                 key.getEndOffset() +
                 " will replace into: " +
                 StringUtil.join(value.getList(), "\n"));
      }
      LOG.debug("<------ GenericPatchApplier.printTransformations");
    }
  }

  public int weightContextMatch(final int maxWalk, final int maxPartsToCheck) {
    final List<SplitHunk> hunks = new ArrayList<SplitHunk>(myHunks.size());
    for (PatchHunk hunk : myHunks) {
      hunks.addAll(SplitHunk.read(hunk));
    }
    int cntPlus = 0;
    int cnt = maxPartsToCheck;
    for (SplitHunk hunk : hunks) {
      final SplitHunk copy = createWithAllContextCopy(hunk);
      if (copy.isInsertion()) continue;
      if (testForPartialContextMatch(copy, new ExactMatchSolver(copy), maxWalk)) {
        ++ cntPlus;
      }
      -- cnt;
      if (cnt == 0) break;
    }
    return cntPlus;
  }

  public boolean execute() {
    debug("GenericPatchApplier execute started");
    if (! myHunks.isEmpty()) {
      mySuppressNewLineInEnd = myHunks.get(myHunks.size() - 1).isNoNewLineAtEnd();
    }
    for (final PatchHunk hunk : myHunks) {
      myNotExact.addAll(SplitHunk.read(hunk));
    }
    for (Iterator<SplitHunk> iterator = myNotExact.iterator(); iterator.hasNext(); ) {
      SplitHunk splitHunk = iterator.next();
      final SplitHunk copy = createWithAllContextCopy(splitHunk);
      if (testForExactMatch(copy)) {
        iterator.remove();
      }
    }
    printTransformations("after exact match");
    /*for (SplitHunk hunk : myNotExact) {
      complementInsertAndDelete(hunk);
    }*/

    for (Iterator<SplitHunk> iterator = myNotExact.iterator(); iterator.hasNext(); ) {
      SplitHunk hunk = iterator.next();
      final SplitHunk copy = createWithAllContextCopy(hunk);
      if (copy.isInsertion()) continue;
      if (testForPartialContextMatch(copy, new ExactMatchSolver(copy), ourMaxWalk)) {
        iterator.remove();
      }
    }
    printTransformations("after exact but without context");
    for (SplitHunk hunk : myNotExact) {
      complementInsertAndDelete(hunk);
    }
    for (Iterator<SplitHunk> iterator = myNotExact.iterator(); iterator.hasNext(); ) {
      SplitHunk hunk = iterator.next();
      if (hunk.isInsertion()) continue;
      if (testForPartialContextMatch(hunk, new ExactMatchSolver(hunk), ourMaxWalk)) {
        iterator.remove();
      }
    }
    printTransformations("after variable place match");

    return myNotExact.isEmpty();
  }

  private SplitHunk createWithAllContextCopy(final SplitHunk hunk) {
    final SplitHunk copy = new SplitHunk(hunk.getStartLineBefore(), new ArrayList<BeforeAfter<List<String>>>(hunk.getPatchSteps()),
                                              new ArrayList<String>(),
                                              new ArrayList<String>());

    final List<BeforeAfter<List<String>>> steps = copy.getPatchSteps();
    final BeforeAfter<List<String>> first = steps.get(0);
    final BeforeAfter<List<String>> last = steps.get(steps.size() - 1);

    final BeforeAfter<List<String>> firstCopy = copyBeforeAfter(first);
    steps.set(0, firstCopy);
    firstCopy.getBefore().addAll(0, hunk.getContextBefore());
    firstCopy.getAfter().addAll(0, hunk.getContextBefore());

    if (first == last) {
      firstCopy.getBefore().addAll(hunk.getContextAfter());
      firstCopy.getAfter().addAll(hunk.getContextAfter());
    } else {
      final BeforeAfter<List<String>> lastCopy = copyBeforeAfter(last);
      lastCopy.getBefore().addAll(hunk.getContextAfter());
      lastCopy.getAfter().addAll(hunk.getContextAfter());
    }
    return copy;
  }

  private BeforeAfter<List<String>> copyBeforeAfter(BeforeAfter<List<String>> first) {
    return new BeforeAfter<List<String>>(new ArrayList<String>(first.getBefore()),
                                                                              new ArrayList<String>(first.getAfter()));
  }

  private void complementInsertAndDelete(final SplitHunk hunk) {
    final List<BeforeAfter<List<String>>> steps = hunk.getPatchSteps();
    final BeforeAfter<List<String>> first = steps.get(0);
    final BeforeAfter<List<String>> last = steps.get(steps.size() - 1);
    final boolean complementFirst = first.getBefore().isEmpty() || first.getAfter().isEmpty();
    final boolean complementLast = last.getBefore().isEmpty() || last.getAfter().isEmpty();

    final List<String> contextBefore = hunk.getContextBefore();
    if (complementFirst && ! contextBefore.isEmpty()) {
      final String firstContext = contextBefore.get(contextBefore.size() - 1);
      first.getBefore().add(0, firstContext);
      first.getAfter().add(0, firstContext);
      contextBefore.remove(contextBefore.size() - 1);
    }
    final List<String> contextAfter = hunk.getContextAfter();
    if (complementLast && ! contextAfter.isEmpty()) {
      final String firstContext = contextAfter.get(0);
      last.getBefore().add(firstContext);
      last.getAfter().add(firstContext);
      contextAfter.remove(0);
    }
  }

  private boolean complementIfShort(final SplitHunk hunk) {
    final List<BeforeAfter<List<String>>> steps = hunk.getPatchSteps();
    if (steps.size() > 1) return false;
    final BeforeAfter<List<String>> first = steps.get(0);
    final boolean complementFirst = first.getBefore().isEmpty() || first.getAfter().isEmpty() ||
      first.getBefore().size() == 1 || first.getAfter().size() == 1;
    if (! complementFirst) return false;

    final List<String> contextBefore = hunk.getContextBefore();
    if (! contextBefore.isEmpty()) {
      final String firstContext = contextBefore.get(contextBefore.size() - 1);
      first.getBefore().add(0, firstContext);
      first.getAfter().add(0, firstContext);
      contextBefore.remove(contextBefore.size() - 1);
      return true;
    }
    final List<String> contextAfter = hunk.getContextAfter();
    if (! contextAfter.isEmpty()) {
      final String firstContext = contextAfter.get(0);
      first.getBefore().add(firstContext);
      first.getAfter().add(firstContext);
      contextAfter.remove(0);
      return true;
    }
    return false;
  }

  // applies in a way that patch _can_ be solved manually even in the case of total mismatch
  public void trySolveSomehow() {
    assert ! myNotExact.isEmpty();
    for (Iterator<SplitHunk> iterator = myNotExact.iterator(); iterator.hasNext(); ) {
      final SplitHunk hunk = iterator.next();
      hunk.cutSameTail();
      if (! testForPartialContextMatch(hunk, new LongTryMismatchSolver(hunk), ourMaxWalk)) {
        if (complementIfShort(hunk)) {
          if (! testForPartialContextMatch(hunk, new LongTryMismatchSolver(hunk), ourMaxWalk)) {
            myNotBound.add(hunk);
          }
        } else {
          myNotBound.add(hunk);
        }
      }
    }
    Collections.sort(myNotBound, HunksComparator.getInstance());
    myNotExact.clear();
  }
  
  private boolean testForPartialContextMatch(final SplitHunk splitHunk, final MismatchSolver mismatchSolver, final int maxWalkFromBinding) {
    final List<BeforeAfter<List<String>>> steps = splitHunk.getPatchSteps();
    final BeforeAfter<List<String>> first = steps.get(0);
    final BetterPoint betterPoint = new BetterPoint();

    if (splitHunk.isInsertion()) return false;

    // if it is not just insertion, then in first step will be both parts
    final Iterator<FirstLineDescriptor> iterator = mismatchSolver.getStartLineVariationsIterator();
    while (iterator.hasNext() && (betterPoint.getPoint() == null || ! betterPoint.getPoint().idealFound())) {
      final FirstLineDescriptor descriptor = iterator.next();

      final Iterator<Integer> matchingIterator =
        getMatchingIterator(descriptor.getLine(), splitHunk.getStartLineBefore() + descriptor.getOffset(), maxWalkFromBinding);
      while (matchingIterator.hasNext() && (betterPoint.getPoint() == null || ! betterPoint.getPoint().idealFound())) {
        final Integer lineNumber = matchingIterator.next();

        // go back and forward from point
        final List<BeforeAfter<List<String>>> patchSteps = splitHunk.getPatchSteps();
        final BeforeAfter<List<String>> step = patchSteps.get(descriptor.getStepNumber());
        final FragmentResult fragmentResult = checkFragmented(lineNumber, descriptor.getOffsetInStep(), step, descriptor.isIsInBefore());

        // we go back - if middle fragment ok
        if (descriptor.getStepNumber() > 0 && fragmentResult.isStartAtEdge()) {
          // not including step number here
          final List<BeforeAfter<List<String>>> list = Collections.unmodifiableList(patchSteps.subList(0, descriptor.getStepNumber()));
          int offsetForStart = - descriptor.getOffsetInStep() - 1;
          final SequentialStepsChecker backChecker = new SequentialStepsChecker(lineNumber + offsetForStart, false);
          backChecker.go(list);

          fragmentResult.setContainAlreadyApplied(fragmentResult.isContainAlreadyApplied() || backChecker.isUsesAlreadyApplied());
          fragmentResult.setStart(fragmentResult.getStart() - backChecker.getSizeOfFragmentToBeReplaced());
          fragmentResult.addDistance(backChecker.getDistance());
          fragmentResult.setStartAtEdge(backChecker.getDistance() == 0);
        }

        if (steps.size() > descriptor.getStepNumber() + 1 && fragmentResult.isEndAtEdge()) {
          // forward
          final List<BeforeAfter<List<String>>> list = Collections.unmodifiableList(patchSteps.subList(descriptor.getStepNumber() + 1, patchSteps.size()));
          if (! list.isEmpty()) {
            final SequentialStepsChecker checker = new SequentialStepsChecker(fragmentResult.getEnd() + 1, true);
            checker.go(list);

            fragmentResult.setContainAlreadyApplied(fragmentResult.isContainAlreadyApplied() || checker.isUsesAlreadyApplied());
            fragmentResult.setEnd(fragmentResult.getEnd() + checker.getSizeOfFragmentToBeReplaced());
            fragmentResult.addDistance(checker.getDistance());
            fragmentResult.setEndAtEdge(checker.getDistance() == 0);
          }
        }

        final TextRange textRangeInOldDocument = new UnfairTextRange(fragmentResult.getStart(), fragmentResult.getEnd());
        // ignore too short fragments
        //if (pointCanBeUsed(textRangeInOldDocument) && (! mismatchSolver.isAllowMismatch() || fragmentResult.getEnd() - fragmentResult.getStart() > 1)) {
        if (pointCanBeUsed(textRangeInOldDocument)) {
          final int distance = fragmentResult.myDistance;
          final int commonPart = fragmentResult.getEnd() - fragmentResult.getStart() + 1;
          int contextDistance = 0;
          if (distance == 0 || commonPart < 2) {
            final int distanceBack = getDistanceBack(fragmentResult.getStart() - 1, splitHunk.getContextBefore());
            final int distanceInContextAfter = getDistance(fragmentResult.getEnd() + 1, splitHunk.getContextAfter());
            contextDistance = distanceBack + distanceInContextAfter;
          }
          betterPoint.feed(new Point(distance, textRangeInOldDocument, fragmentResult.isContainAlreadyApplied(), contextDistance, commonPart));
        }
      }
    }
    final Point pointPoint = betterPoint.getPoint();
    if (pointPoint == null) return false;
    if (! mismatchSolver.isAllowMismatch()) {
      if (pointPoint.getDistance() > 0) return false;
      if (pointPoint.myCommon < 2) {
        final int contextCommon = splitHunk.getContextBefore().size() + splitHunk.getContextAfter().size() - pointPoint.myContextDistance;
        if (contextCommon == 0) return false;
      }
    }
    putCutIntoTransformations(pointPoint.getInOldDocument(), new MyAppliedData(splitHunk.getAfterAll(), pointPoint.myUsesAlreadyApplied,
        false, pointPoint.getDistance() == 0, ChangeType.REPLACE));
    return true;
  }

  private FragmentResult checkFragmented(final int lineInTheMiddle, final int offsetInStep, final BeforeAfter<List<String>> step, final boolean inBefore) {
    final List<String> lines = inBefore ? step.getBefore() : step.getAfter();
    final List<String> start = lines.subList(0, offsetInStep);
    int startDistance = 0;
    if (! start.isEmpty()) {
      if (lineInTheMiddle - 1 < 0) {
        startDistance = start.size();
      } else {
        startDistance = getDistanceBack(lineInTheMiddle - 1, start);
      }
    }
    final List<String> end = lines.subList(offsetInStep, lines.size());
    int endDistance = 0;
    if (! end.isEmpty()) {
      endDistance = getDistance(lineInTheMiddle, end);
    }
    final FragmentResult fragmentResult =
      new FragmentResult(lineInTheMiddle - (start.size() - startDistance), lineInTheMiddle + (end.size() - endDistance) - 1, !inBefore);
    fragmentResult.addDistance(startDistance + endDistance);
    fragmentResult.setStartAtEdge(startDistance == 0);
    fragmentResult.setEndAtEdge(endDistance == 0);
    return fragmentResult;
  }

  private static class FragmentResult {
    private int myStart;
    private int myEnd;
    private boolean myContainAlreadyApplied;
    private int myDistance;

    private boolean myStartAtEdge;
    private boolean myEndAtEdge;

    private FragmentResult(int start, int end, boolean containAlreadyApplied) {
      myStart = start;
      myEnd = end;
      myContainAlreadyApplied = containAlreadyApplied;
      myDistance = 0;
    }

    public boolean isStartAtEdge() {
      return myStartAtEdge;
    }

    public void setStartAtEdge(boolean startAtEdge) {
      myStartAtEdge = startAtEdge;
    }

    public boolean isEndAtEdge() {
      return myEndAtEdge;
    }

    public void setEndAtEdge(boolean endAtEdge) {
      myEndAtEdge = endAtEdge;
    }

    public void addDistance(final int distance) {
      myDistance += distance;
    }

    public int getStart() {
      return myStart;
    }

    public int getEnd() {
      return myEnd;
    }

    public boolean isContainAlreadyApplied() {
      return myContainAlreadyApplied;
    }

    public void setStart(int start) {
      myStart = start;
    }

    public void setEnd(int end) {
      myEnd = end;
    }

    public void setContainAlreadyApplied(boolean containAlreadyApplied) {
      myContainAlreadyApplied = containAlreadyApplied;
    }
  }

  private int getDistanceBack(final int idxStart, final List<String> lines) {
    if (idxStart < 0) return lines.size();
    int cnt = lines.size() - 1;
    for (int i = idxStart; i >= 0 && cnt >= 0; i--, cnt--) {
      if (! myLines.get(i).equals(lines.get(cnt))) return (cnt + 1);
    }
    return cnt + 1;
  }

  private int getDistance(final int idxStart, final List<String> lines) {
    if (idxStart >= myLines.size()) return lines.size();
    int cnt = 0;
    for (int i = idxStart; i < myLines.size() && cnt < lines.size(); i++, cnt++) {
      if (! myLines.get(i).equals(lines.get(cnt))) return (lines.size() - cnt);
    }
    return lines.size() - cnt;
  }

  public void putCutIntoTransformations(TextRange range, final MyAppliedData value) {
    // cut last lines but not the very first
    final List<String> list = value.getList();
    int cnt = list.size() - 1;
    int i = range.getEndOffset();
    for (; i > range.getStartOffset() && cnt >= 0; i--, cnt--) {
      if (! list.get(cnt).equals(myLines.get(i))) {
        break;
      }
    }
    int endSize = list.size();
    if (cnt + 1 <= list.size() - 1) {
      endSize = cnt + 1;
    }
    int cntStart = 0;
    int j = range.getStartOffset();

    if (endSize > 0) {
      for (; j < range.getEndOffset() && cntStart < list.size(); j++, cntStart++) {
        if (! list.get(cntStart).equals(myLines.get(j))) {
          break;
        }
      }
    }

    if (j != range.getStartOffset() || i != range.getEndOffset()) {
      if (cntStart >= endSize) {
        // +1 since we set end index inclusively
        if (list.size() == (range.getLength() + 1)) {
          // for already applied
          myHadAlreadyAppliedMet = value.isHaveAlreadyApplied();
        } else {
          // deletion
          myTransformations.put(new UnfairTextRange(j, i + (cntStart - endSize)), new MyAppliedData(Collections.<String>emptyList(), value.isHaveAlreadyApplied(),
                                                                   value.isPlaceCoinside(), value.isChangedCoinside(), value.myChangeType));
        }
      } else {
        if (i < j) {
          // insert case
          // just took one line
          assert cntStart > 0;
          final MyAppliedData newData =
            new MyAppliedData(new ArrayList<String>(list.subList(cntStart - (j - i), endSize)), value.isHaveAlreadyApplied(), value.isPlaceCoinside(),
                            value.isChangedCoinside(), value.myChangeType);
          final TextRange newRange = new TextRange(i, i);
          myTransformations.put(newRange, newData);
          return;
        }
        final MyAppliedData newData =
          new MyAppliedData(new ArrayList<String>(list.subList(cntStart, endSize)), value.isHaveAlreadyApplied(), value.isPlaceCoinside(),
                          value.isChangedCoinside(), value.myChangeType);
        final TextRange newRange = new TextRange(j, i);
        myTransformations.put(newRange, newData);
      }
    } else {
      myTransformations.put(range, value);
    }
  }

  private boolean pointCanBeUsed(final TextRange range) {
    // we check with offset only one
    final Map.Entry<TextRange, MyAppliedData> entry = myTransformations.ceilingEntry(range);
    if (entry != null && entry.getKey().intersects(range)) {
      return false;
    }
    return true;
  }

  private static class BetterPoint {
    private Point myPoint;

    public void feed(@NotNull final Point point) {
      if (myPoint == null || point.meBetter(myPoint)) {
        myPoint = point;
      }
    }

    public Point getPoint() {
      return myPoint;
    }
  }

  private static class Point {
    private int myDistance;
    private int myContextDistance;
    private int myCommon;
    private final boolean myUsesAlreadyApplied;
    private TextRange myInOldDocument;

    private Point(int distance, TextRange inOldDocument, boolean usesAlreadyApplied, int contextDistance, int common) {
      myDistance = distance;
      myInOldDocument = inOldDocument;
      myUsesAlreadyApplied = usesAlreadyApplied;
      myContextDistance = contextDistance;
      myCommon = common;
    }
    
    public boolean meBetter(final Point maxPoint) {
      if (myCommon <= 1 && maxPoint.myCommon > 1) return false;
      if (maxPoint.myCommon <= 1 && myCommon > 1) return true;

      return (myDistance < maxPoint.getDistance()) || (myDistance == 0 && maxPoint.getDistance() == 0 && myContextDistance < maxPoint.myContextDistance);
    }

    public int getDistance() {
      return myDistance;
    }

    public boolean idealFound() {
      return myDistance == 0 && myContextDistance == 0;
    }

    public TextRange getInOldDocument() {
      return myInOldDocument;
    }
  }

  private class SequentialStepsChecker implements SequenceDescriptor {
    private int myDistance;
    // in the end, will be [excluding] end of changing interval
    private int myIdx;
    private int myStartIdx;
    private final boolean myForward;
    private boolean myUsesAlreadyApplied;

    private SequentialStepsChecker(int lineNumber, boolean forward) {
      myStartIdx = lineNumber;
      myIdx = lineNumber;
      myForward = forward;
    }

    public boolean isUsesAlreadyApplied() {
      return myUsesAlreadyApplied;
    }

    @Override
    public int getDistance() {
      return myDistance;
    }

    public void go(final List<BeforeAfter<List<String>>> steps) {
      final Consumer<BeforeAfter<List<String>>> stepConsumer = new Consumer<BeforeAfter<List<String>>>() {
        @Override
        public void consume(BeforeAfter<List<String>> listBeforeAfter) {
          if (myDistance == 0) {
            // until this point, it all had being doing well
            if (listBeforeAfter.getBefore().isEmpty()) {
              // just take it
              return;
            }
            final FragmentMatcher fragmentMatcher = new FragmentMatcher(myIdx, listBeforeAfter);
            final Pair<Integer, Boolean> pair = fragmentMatcher.find(true);
            myDistance = pair.getFirst();
            if (myDistance == 0) {
              myIdx += pair.getSecond() ? listBeforeAfter.getBefore().size() : listBeforeAfter.getAfter().size();
            } else {
              myIdx += (pair.getSecond() ? listBeforeAfter.getBefore().size() : listBeforeAfter.getAfter().size()) - pair.getFirst();
            }
            myUsesAlreadyApplied = ! pair.getSecond();
          } else {
            // we just count the distance
            myDistance += listBeforeAfter.getBefore().size();
          }
        }
      };

      if (myForward) {
        for (BeforeAfter<List<String>> step : steps) {
          stepConsumer.consume(step);
        }
      } else {
        for (int i = steps.size() - 1; i >= 0; i--) {
          BeforeAfter<List<String>> step = steps.get(i);
          stepConsumer.consume(step);
        }
      }
    }
    
    @Override
    public int getSizeOfFragmentToBeReplaced() {
      return myForward ? (myIdx - myStartIdx) : (myStartIdx - myIdx);
    }
  }

  private static class FirstLineDescriptor {
    private final String myLine;
    private final int myOffset;
    private final int myStepNumber;
    private final int myOffsetInStep;
    private final boolean myIsInBefore;

    private FirstLineDescriptor(String line, int offset, int stepNumber, int offsetInStep, boolean isInBefore) {
      myLine = line;
      myOffset = offset;
      myStepNumber = stepNumber;
      myOffsetInStep = offsetInStep;
      myIsInBefore = isInBefore;
    }

    public String getLine() {
      return myLine;
    }

    public int getOffset() {
      return myOffset;
    }

    public int getStepNumber() {
      return myStepNumber;
    }

    public int getOffsetInStep() {
      return myOffsetInStep;
    }

    public boolean isIsInBefore() {
      return myIsInBefore;
    }
  }
  
  private static class ExactMatchSolver extends MismatchSolver {
    private ExactMatchSolver(final SplitHunk hunk) {
      super(false);
      final List<BeforeAfter<List<String>>> steps = hunk.getPatchSteps();
      final BeforeAfter<List<String>> first = steps.get(0);
      if (steps.size() == 1 && first.getBefore().isEmpty()) {
        myResult.add(new FirstLineDescriptor(first.getBefore().get(0), 0, 0,0,true));
      }
      if (! first.getBefore().isEmpty()) {
        myResult.add(new FirstLineDescriptor(first.getBefore().get(0), 0, 0,0,true));
      }
      if (! first.getAfter().isEmpty()) {
        myResult.add(new FirstLineDescriptor(first.getAfter().get(0), 0, 0,0,false));
      }
      assert ! myResult.isEmpty();
    }
  }
  
  public static class LongTryMismatchSolver extends MismatchSolver {
    // let it be 3 first steps plus 2 lines as an attempt
    public LongTryMismatchSolver(final SplitHunk hunk) {
      super(true);
      final List<BeforeAfter<List<String>>> steps = hunk.getPatchSteps();
      int beforeOffset = 0;
      int afterOffset = 0;
      for (int i = 0; i < steps.size() && i < 3; i++) {
        final BeforeAfter<List<String>> list = steps.get(i);
        final List<String> before = list.getBefore();
        for (int j = 0; j < before.size() && j < 2; j++) {
          final String s = before.get(j);
          myResult.add(new FirstLineDescriptor(s, beforeOffset + j, i, j, true));
        }
        final List<String> after = list.getAfter();
        for (int j = 0; j < after.size() && j < 2; j++) {
          final String s = after.get(j);
          myResult.add(new FirstLineDescriptor(s, afterOffset + j, i, j, false));
        }
        beforeOffset += before.size();
        afterOffset += after.size();
      }
    }
  }
  
  private abstract static class MismatchSolver {
    protected final ArrayList<FirstLineDescriptor> myResult;
    private final boolean myAllowMismatch;

    protected MismatchSolver(boolean allowMismatch) {
      myResult = new ArrayList<FirstLineDescriptor>();
      myAllowMismatch = allowMismatch;
    }

    public Iterator<FirstLineDescriptor> getStartLineVariationsIterator() {
      return myResult.iterator();
    }

    public boolean isAllowMismatch() {
      return myAllowMismatch;
    }
  }
  
  private Iterator<Integer> getMatchingIterator(final String line, final int originalStart, final int maxWalkFromBinding) {
    return ContainerUtil.concatIterators(new WalkingIterator(line, originalStart, maxWalkFromBinding, true),
                                          new WalkingIterator(line, originalStart, maxWalkFromBinding, false));
  }
  
  private class WalkingIterator implements Iterator<Integer> {
    private final String myLine;
    // true = down
    private final boolean myDirection;

    private int myLeftWalk;
    // == -1 when end
    private int myCurrentIdx;

    private WalkingIterator(String line, int start, int leftWalk, boolean direction) {
      myLine = line;
      myLeftWalk = leftWalk;

      myDirection = direction;
      myCurrentIdx = start - 1;
      step();
    }

    @Override
    public boolean hasNext() {
      return myCurrentIdx != -1;
    }

    @Override
    public Integer next() {
      final int currentIdx = myCurrentIdx;
      step();
      return currentIdx;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    public void step() {
      if (myDirection) {
        int i = myCurrentIdx + 1;
        int maxWalk = myLeftWalk + i;
        myCurrentIdx = -1;
        for (; i < myLines.size() && i < maxWalk; i++) {
          final String s = myLines.get(i);
          if (myLine.equals(s) && ! isSeized(i)) {
            myCurrentIdx = i;
            myLeftWalk = maxWalk - 1 - i;
            break;
          }
        }
      } else {
        int i = myCurrentIdx;
        int maxWalk = Math.max(-1, i - myLeftWalk);
        myCurrentIdx = -1;
        for (; i >= 0 && i > maxWalk && i < myLines.size(); i--) {
          final String s = myLines.get(i);
          if (myLine.equals(s) && ! isSeized(i)) {
            myCurrentIdx = i;
            myLeftWalk = i - (maxWalk + 1); // todo +-
            break;
          }
        }
      }
    }
    
    private boolean isSeized(final int lineNumber) {
      final TextRange art = new TextRange(lineNumber, lineNumber);
      final TextRange floor = myTransformations.floorKey(art);
      return (floor != null && floor.intersects(art));
    }
  }

  private boolean testForExactMatch(final SplitHunk splitHunk) {
    final int offset = splitHunk.getContextBefore().size();
    final List<BeforeAfter<List<String>>> steps = splitHunk.getPatchSteps();
    if (splitHunk.isInsertion()) {
      final boolean emptyFile = myLines.isEmpty() || myLines.size() == 1 && myLines.get(0).trim().length() == 0;
      if (emptyFile) {
        myNotBound.add(splitHunk);
      }
      return emptyFile;
    }

    int idx = splitHunk.getStartLineBefore() + offset;
    int cnt = 0;
    boolean hadAlreadyApplied = false;
    for (BeforeAfter<List<String>> step : steps) {
      if (myLines.size() <= idx) return false;
      if (step.getBefore().isEmpty()) continue; // can occur only in the end

      final Pair<Integer, Boolean> distance = new FragmentMatcher(idx + cnt, step).find(false);
      if (distance.getFirst() > 0) {
        return false;
      }
      // fits!
      int length;
      if (distance.getSecond()) {
        length = step.getBefore().size();
      }
      else {
        length = step.getAfter().size();
        hadAlreadyApplied = true;
      }
      cnt += length;
      //idx += length - 1;
    }
    putCutIntoTransformations(new TextRange(idx, idx + cnt - 1),
                              new MyAppliedData(splitHunk.getAfterAll(), hadAlreadyApplied, true, true, ChangeType.REPLACE));
    return true;
  }

  // will not find consider fragments that intersect
  private class FragmentMatcher {
    private int myIdx;
    private int myOffsetIdxInHunk;
    // if we set index in hunk != 0, then we will check only one side
    private Boolean myBeforeSide;
    private boolean myIsInBefore;
    private int myIdxInHunk;
    private final BeforeAfter<List<String>> myBeforeAfter;

    private FragmentMatcher(int idx, BeforeAfter<List<String>> beforeAfter) {
      myOffsetIdxInHunk = 0;
      myIdx = idx;
      myBeforeAfter = beforeAfter;
      myIdxInHunk = 0;
      //myBeforeSide = true;
    }
    
    public void setSideAndIdx(final int startInHunk, final boolean beforeSide) {
      myOffsetIdxInHunk = startInHunk;
      myBeforeSide = beforeSide;
      if (myBeforeSide) {
        assert myBeforeAfter.getBefore().size() > myOffsetIdxInHunk || (myOffsetIdxInHunk == 0 && myBeforeAfter.getBefore().size() == 0);
      } else {
        assert myBeforeAfter.getAfter().size() > myOffsetIdxInHunk || (myOffsetIdxInHunk == 0 && myBeforeAfter.getAfter().size() == 0);
      }
    }

    // true = before
    public Pair<Integer, Boolean> find(final boolean canMismatch) {
      if (myBeforeSide != null) {
        if (myBeforeSide) {
          // check only one side
          return new Pair<Integer, Boolean>(checkSide(myBeforeAfter.getBefore(), canMismatch), true);
        } else {
          // check only one side
          return new Pair<Integer, Boolean>(checkSide(myBeforeAfter.getAfter(), canMismatch), false);
        }
      } else {
        final int beforeCheckResult = checkSide(myBeforeAfter.getBefore(), canMismatch);
        final int afterCheckResult = checkSide(myBeforeAfter.getAfter(), canMismatch);

        final Pair<Integer, Boolean> beforePair = new Pair<Integer, Boolean>(beforeCheckResult, true);
        final Pair<Integer, Boolean> afterPair = new Pair<Integer, Boolean>(afterCheckResult, false);
        if (! canMismatch) {
          if (beforeCheckResult == 0) {
            return beforePair;
          }
          if (afterCheckResult == 0) {
            return afterPair;
          }
          return beforePair;
        }

        // take longer coinsiding
        final int beforeCommon = myBeforeAfter.getBefore().size() - beforeCheckResult;
        final int afterCommon = myBeforeAfter.getAfter().size() - afterCheckResult;
        if (beforeCommon > 0 && afterCommon > 0) {
          // ignore insertion and deletion cases -> too few context -> if we have alternative
          if (beforeCommon == 1 && myBeforeAfter.getBefore().size() == 1 && afterCommon > 1) {
            return afterPair;
          }
          // ignore insertion and deletion cases -> too few context -> if we have alternative
          if (afterCommon == 1 && myBeforeAfter.getAfter().size() == 1 && beforeCommon > 1) {
            return beforePair;
          }
          if (beforeCommon >= afterCommon) {
            return beforePair;
          }
          return afterPair;
        }
        if (afterCommon > 0) {
          return afterPair;
        }
        return beforePair;
      }
    }
    
    private int checkSide(final List<String> side, final boolean canMismatch) {
      int distance = 0;
      if (myOffsetIdxInHunk > 0) {
        int linesIdx = myIdx - 1;
        int i = myOffsetIdxInHunk - 1;
        for (; i >= 0 && linesIdx >= 0; i--, linesIdx--) {
          if (! myLines.get(linesIdx).equals(side.get(i))) {
            break;
          }
        }
        // since ok -> === -1
        i += 1;
        if (i > 0 && ! canMismatch) return i; // don't go too deep if we need just == or > 0
        distance = i;
      }
      int linesEndIdx = myIdx;
      int j = myOffsetIdxInHunk;
      for (; j < side.size() && linesEndIdx < myLines.size(); j++, linesEndIdx++) {
        if (! myLines.get(linesEndIdx).equals(side.get(j))) {
          break;
        }
      }
      distance += side.size() - j;
      return distance;
    }
  }

  public String getAfter() {
    final StringBuilder sb = new StringBuilder();
    // put not bind into the beginning
    for (SplitHunk hunk : myNotBound) {
      linesToSb(sb, hunk.getAfterAll());
    }
    iterateTransformations(new Consumer<TextRange>() {
      @Override
      public void consume(TextRange range) {
        linesToSb(sb, myLines.subList(range.getStartOffset(), range.getEndOffset() + 1));
      }
    }, new Consumer<TextRange>() {
      @Override
      public void consume(TextRange range) {
        final MyAppliedData appliedData = myTransformations.get(range);
        if (appliedData.isInsertAfter()) {
          if (sb.length() > 0) {
            sb.append('\n');
          }
          sb.append(myLines.get(range.getStartOffset()));
        }
        linesToSb(sb, appliedData.getList());
      }
    });
    if (! mySuppressNewLineInEnd) {
      sb.append('\n');
    }
    return sb.toString();
  }

  private void linesToSb(final StringBuilder sb, final List<String> list) {
    for (String s : list) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(s);
    }
  }

  // indexes are passed inclusive
  private void iterateTransformations(final Consumer<TextRange> consumerExcluded, final Consumer<TextRange> consumerIncluded) {
    if (myTransformations.isEmpty()) {
      consumerExcluded.consume(new UnfairTextRange(0, myLines.size() - 1));
    } else {
      final Set<Map.Entry<TextRange,MyAppliedData>> entries = myTransformations.entrySet();
      final Iterator<Map.Entry<TextRange, MyAppliedData>> iterator = entries.iterator();
      assert iterator.hasNext();

      final Map.Entry<TextRange, MyAppliedData> first = iterator.next();
      final TextRange range = first.getKey();
      if (range.getStartOffset() > 0) {
        consumerExcluded.consume(new TextRange(0, range.getStartOffset() - 1));
      }
      consumerIncluded.consume(range);

      int previousEnd = range.getEndOffset() + 1;
      while (iterator.hasNext() && previousEnd < myLines.size()) {
        final Map.Entry<TextRange, MyAppliedData> entry = iterator.next();
        final TextRange key = entry.getKey();
        consumerExcluded.consume(new UnfairTextRange(previousEnd, key.getStartOffset() - 1));
        consumerIncluded.consume(key);
        previousEnd = key.getEndOffset() + 1;
      }
      if (previousEnd < myLines.size()) {
        consumerExcluded.consume(new TextRange(previousEnd, myLines.size() - 1));
      }
    }
  }

  public static class SplitHunk {
    private final List<String> myContextBefore;
    private final List<String> myContextAfter;
    private final List<BeforeAfter<List<String>>> myPatchSteps;
    private final int myStartLineBefore;

    public SplitHunk(int startLineBefore,
                      List<BeforeAfter<List<String>>> patchSteps,
                      List<String> contextAfter,
                      List<String> contextBefore) {
      myStartLineBefore = startLineBefore;
      myPatchSteps = patchSteps;
      myContextAfter = contextAfter;
      myContextBefore = contextBefore;
    }

    // todo
    public void cutSameTail() {
      final BeforeAfter<List<String>> lastStep = myPatchSteps.get(myPatchSteps.size() - 1);
      final List<String> before = lastStep.getBefore();
      final List<String> after = lastStep.getAfter();
      int cntBefore = before.size() - 1;
      int cntAfter = after.size() - 1;

      for (; cntBefore >= 0 && cntAfter > 0; cntBefore--, cntAfter--) {
        if (! before.get(cntBefore).equals(after.get(cntAfter))) break;
      }

      // typically only 1 line
      int cutSame = before.size() - 1 - cntBefore;
      for (int i = 0; i < cutSame; i++) {
        before.remove(before.size() - 1);
        after.remove(after.size() - 1);
      }
    }

    public static List<SplitHunk> read(final PatchHunk hunk) {
      final List<SplitHunk> result = new ArrayList<SplitHunk>();
      final List<PatchLine> lines = hunk.getLines();
      int i = 0;

      List<String> contextBefore = new ArrayList<String>();
      int newSize = 0;
      while (i < lines.size()) {
        final int inheritedContext = contextBefore.size();
        final List<String> contextAfter = new ArrayList<String>();
        final List<BeforeAfter<List<String>>> steps = new ArrayList<BeforeAfter<List<String>>>();
        final int endIdx = readOne(lines, contextBefore, contextAfter, steps, i);
        result.add(new SplitHunk(hunk.getStartLineBefore() + i - inheritedContext - newSize, steps, contextAfter, contextBefore));
        for (BeforeAfter<List<String>> step : steps) {
          newSize += step.getAfter().size();
        }
        i = endIdx;
        if (i < lines.size()) {
          contextBefore = new ArrayList<String>();
          contextBefore.addAll(contextAfter);
        }
      }
      return result;
    }
    
    private static int readOne(final List<PatchLine> lines, final List<String> contextBefore, final List<String> contextAfter, 
                               final List<BeforeAfter<List<String>>> steps, final int startI) {
      int i = startI;
      for (; i < lines.size(); i++) {
        final PatchLine patchLine = lines.get(i);
        if (! PatchLine.Type.CONTEXT.equals(patchLine.getType())) break;
        contextBefore.add(patchLine.getText());
      }

      final boolean addFirst = i < lines.size() && PatchLine.Type.ADD.equals(lines.get(i).getType());
      List<String> before = new ArrayList<String>();
      List<String> after = new ArrayList<String>();
      for (; i < lines.size(); i++) {
        final PatchLine patchLine = lines.get(i);
        final PatchLine.Type type = patchLine.getType();
        if (PatchLine.Type.CONTEXT.equals(type)) {
          break;
        }
        if (PatchLine.Type.ADD.equals(type)) {
          if (addFirst && ! before.isEmpty()) {
            // new piece
            steps.add(new BeforeAfter<List<String>>(before, after));
            before = new ArrayList<String>();
            after = new ArrayList<String>();
          }
          after.add(patchLine.getText());
        } else if (PatchLine.Type.REMOVE.equals(type)) {
          if (! addFirst && ! after.isEmpty()) {
            // new piece
            steps.add(new BeforeAfter<List<String>>(before, after));
            before = new ArrayList<String>();
            after = new ArrayList<String>();
          }
          before.add(patchLine.getText());
        }
      }
      if (! before.isEmpty() || ! after.isEmpty()) {
        steps.add(new BeforeAfter<List<String>>(before, after));
      }

      for (; i < lines.size(); i++) {
        final PatchLine patchLine = lines.get(i);
        if (! PatchLine.Type.CONTEXT.equals(patchLine.getType())) {
          return i;
        }
        contextAfter.add(patchLine.getText());
      }
      return lines.size();
    }

    public boolean isInsertion() {
      return myPatchSteps.size() == 1 && myPatchSteps.get(0).getBefore().isEmpty();
    }

    public int getStartLineBefore() {
      return myStartLineBefore;
    }

    public List<String> getContextBefore() {
      return myContextBefore;
    }

    public List<String> getContextAfter() {
      return myContextAfter;
    }

    public List<BeforeAfter<List<String>>> getPatchSteps() {
      return myPatchSteps;
    }
    
    public List<String> getAfterAll() {
      final ArrayList<String> after = new ArrayList<String>();
      for (BeforeAfter<List<String>> step : myPatchSteps) {
        after.addAll(step.getAfter());
      }
      return after;
    }
  }

  public static class MyAppliedData {
    private List<String> myList;
    private final boolean myHaveAlreadyApplied;
    private final boolean myPlaceCoinside;
    private final boolean myChangedCoinside;
    private final ChangeType myChangeType;

    public MyAppliedData(List<String> list,
                         boolean alreadyApplied,
                         boolean placeCoinside,
                         boolean changedCoinside,
                         ChangeType changeType) {
      myList = list;
      myHaveAlreadyApplied = alreadyApplied;
      myPlaceCoinside = placeCoinside;
      myChangedCoinside = changedCoinside;
      myChangeType = changeType;
    }

    public List<String> getList() {
      return myList;
    }
    
    public void cutToSize(final int size) {
      assert size > 0 && size < myList.size();
      myList = new ArrayList<String>(myList.subList(0, size));
    }

    public boolean isHaveAlreadyApplied() {
      return myHaveAlreadyApplied;
    }

    public boolean isPlaceCoinside() {
      return myPlaceCoinside;
    }

    public boolean isChangedCoinside() {
      return myChangedCoinside;
    }

    public boolean isInsertAfter() {
      return ChangeType.INSERT_AFTER.equals(myChangeType);
    }
  }

  public static enum ChangeType {
    INSERT_AFTER,
    REPLACE
  }

  public TreeMap<TextRange, MyAppliedData> getTransformations() {
    return myTransformations;
  }
  
  private static class HunksComparator implements Comparator<SplitHunk> {
    private final static HunksComparator ourInstance = new HunksComparator();

    public static HunksComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(SplitHunk o1, SplitHunk o2) {
      return Integer.valueOf(o1.getStartLineBefore()).compareTo(Integer.valueOf(o2.getStartLineBefore()));
    }
  }
}
