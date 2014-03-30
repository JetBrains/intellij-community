/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;

public class ByWord implements DiffPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.ByWord");
  private final ComparisonPolicy myComparisonPolicy;

  public ByWord(ComparisonPolicy comparisonPolicy) {
    myComparisonPolicy = comparisonPolicy;
  }

  @NotNull
  @TestOnly
  public DiffFragment[] buildFragments(@NotNull String text1, @NotNull String text2) throws FilesTooBigForDiffException {
    return buildFragments(DiffString.create(text1), DiffString.create(text2));
  }

  @NotNull
  @Override
  public DiffFragment[] buildFragments(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException {
    Word[] words1 = buildWords(text1, myComparisonPolicy);
    Word[] words2 = buildWords(text2, myComparisonPolicy);
    Diff.Change change = Diff.buildChanges(words1, words2);
    change = Util.concatEquals(change, words1, words2);
    if (Math.max(countNotWhitespaces(words1), countNotWhitespaces(words2)) > 0 && countEqual(change, words1, words2) == 0)
      return new DiffFragment[]{myComparisonPolicy.createFragment(text1, text2)};
    FragmentBuilder result = new FragmentBuilder(words1, words2, myComparisonPolicy, text1, text2);
    FragmentBuilder.Version version1 = result.getVersion1();
    FragmentBuilder.Version version2 = result.getVersion2();
    while (change != null) {
      if (change.line0 > version1.getCurrentWordIndex()) {
        processEquals(change.line0, change.line1, result);
      }
      if (change.inserted == 0) {
        processOneside(version1, change.deleted);
      } else if (change.deleted == 0) {
        processOneside(version2, change.inserted);
      } else {
        DiffString prefix1 = version1.getCurrentWordPrefix();
        DiffString prefix2 = version2.getCurrentWordPrefix();
        if (!prefix1.isEmpty() || !prefix2.isEmpty())
          result.add(myComparisonPolicy.createFragment(prefix1, prefix2));
        result.addChangedWords(change.deleted, change.inserted);
      }
      change = change.link;
    }
    processEquals(words1.length, words2.length, result);
    result.addTails();
    DiffFragment[] fragments = result.getFragments();
    DiffFragment firstFragment = fragments[0];
    if (firstFragment.isEmpty()) {
      DiffFragment[] newFragments = new DiffFragment[fragments.length - 1];
      System.arraycopy(fragments, 1, newFragments, 0, newFragments.length);
      fragments = newFragments;
    }
    return fragments;
  }

  private static int countNotWhitespaces(@NotNull Word[] words) {
    int counter = 0;
    for (int i = 0; i < words.length; i++) {
      Word word = words[i];
      if (!word.isWhitespace()) counter++;
    }
    return counter;
  }

  private static int countEqual(Diff.Change change, @NotNull Word[] words1, @NotNull Word[] words2) {
    int counter = 0;
    int position1 = 0;
    int position2 = 0;
    while (change != null) {
      if (change.line0 > position1) {
        int same = change.line0 - position1;
        LOG.assertTrue(same == change.line1 - position2);
        for (int i = 0; i < same; i++) {
          if (!words1[position1 + i].isWhitespace() && !words2[position2 + i].isWhitespace()) counter++;
        }
        position1 += same;
        position2 += same;
      }
      position1 += change.deleted;
      position2 += change.inserted;
      change = change.link;
    }
    int tailCount = words1.length - position1;
    LOG.assertTrue(tailCount == words2.length - position2);
    while (tailCount > 0) {
      if (!words1[words1.length - tailCount].isWhitespace() &&
          !words2[words2.length - tailCount].isWhitespace()) counter++;
      tailCount--;
    }
    return counter;
  }

  private static void processOneside(@NotNull FragmentBuilder.Version version, int wordCount) {
    DiffString prefix = version.getCurrentWordPrefix();
    version.addOneSide(prefix, wordCount);
  }

  private static void processEquals(int changed1, int changed2, @NotNull FragmentBuilder result) throws FilesTooBigForDiffException {
    while (result.getVersion1().getCurrentWordIndex() < changed1) {
      result.processEqual();
    }
    LOG.assertTrue(changed2 == result.getVersion2().getCurrentWordIndex());
  }

  @NotNull
  @TestOnly
  static Word[] buildWords(@NotNull String text, @NotNull ComparisonPolicy policy) {
    return buildWords(DiffString.create(text), policy);
  }

  @NotNull
  static Word[] buildWords(@NotNull DiffString text, @NotNull ComparisonPolicy policy) {
    ArrayList<Word> words = new ArrayList<Word>();
    if (text.isEmpty() || !Character.isWhitespace(text.charAt(0)))
      words.add(policy.createFormatting(text, TextRange.EMPTY_RANGE));
    int start = 0;
    boolean withinFormatting = true;
    for (int i = 0; i < text.length(); i++) {
      char nextChar = text.charAt(i);
      boolean isWhitespace = Character.isWhitespace(nextChar);
      if (withinFormatting) {
        if (isWhitespace) continue;
        if (start != -1 && start < i) words.add(policy.createFormatting(text, new TextRange(start, i)));
        start = -1;
        withinFormatting = false;
      }
      if (nextChar == '\n') {
        if (start != -1) words.add(new Word(text, new TextRange(start, i)));
        start = i;
        withinFormatting = true;
      } else if (Util.DELIMITERS_SET.contains(nextChar)) {
        if (start != -1) {
          words.add(new Word(text, new TextRange(start, i)));
          start = -1;
        }
      } else {
        if (start == -1) start = i;
      }
    }
    if (start != -1) {
      TextRange range = new TextRange(start, text.length());
      Word lastWord = withinFormatting ? policy.createFormatting(text, range) : new Word(text, range);
      words.add(lastWord);
    }
    return words.toArray(new Word[words.size()]);
  }

  private static class FragmentBuilder {
    private final ArrayList<DiffFragment> myFragments = new ArrayList<DiffFragment>();
    private final Version myVersion1;
    private final Version myVersion2;
    private final DiffPolicy.ByChar BY_CHAR;
    private final DiffCorrection.ChangedSpace CORRECTION;
    private final ComparisonPolicy myComparisonPolicy;

    public FragmentBuilder(@NotNull Word[] words1, @NotNull Word[] words2, @NotNull ComparisonPolicy comparisonPolicy, @NotNull DiffString text1, @NotNull DiffString text2) {
      myVersion1 = new Version(words1, text1, this, true);
      myVersion2 = new Version(words2, text2, this, false);
      BY_CHAR = new ByChar(comparisonPolicy);
      CORRECTION = new DiffCorrection.ChangedSpace(comparisonPolicy);
      myComparisonPolicy = comparisonPolicy;
    }

    @NotNull
    public DiffFragment[] getFragments() {
      return myFragments.toArray(new DiffFragment[myFragments.size()]);
    }

    @NotNull
    public Version getVersion1() { return myVersion1; }

    @NotNull
    public Version getVersion2() { return myVersion2; }

    private void addAll(@NotNull DiffFragment[] fragments) {
      for (int i = 0; i < fragments.length; i++) {
        DiffFragment fragment = fragments[i];
        add(fragment);
      }
    }

    private void add(@NotNull DiffFragment fragment) {
      DiffString text1 = fragment.getText1();
      DiffString text2 = fragment.getText2();
      if (text1 != null) myVersion1.addOffset(text1.length());
      if (text2 != null) myVersion2.addOffset(text2.length());
      if (fragment.isEqual() && !myFragments.isEmpty()) {
        int lastIndex = myFragments.size() - 1;
        DiffFragment prevFragment = myFragments.get(lastIndex);
        if (prevFragment.isEqual()) {
          prevFragment.appendText1(text1);
          prevFragment.appendText2(text2);
          return;
        }
      }
      myFragments.add(fragment);
    }

    private void addEqual(@NotNull Word word1, @NotNull Word word2) throws FilesTooBigForDiffException {
      addAll(CORRECTION.correct(new DiffFragment[]{myComparisonPolicy.createFragment(word1, word2)}));
    }

    public void processEqual() throws FilesTooBigForDiffException {
      Word word1 = myVersion1.getCurrentWord();
      Word word2 = myVersion2.getCurrentWord();
      addAll(fragmentsByChar(myVersion1.getCurrentWordPrefix(), myVersion2.getCurrentWordPrefix()));
      addEqual(word1, word2);
      addPostfixes();
      myVersion1.incCurrentWord();
      myVersion2.incCurrentWord();
    }

    @NotNull
    private DiffFragment[] fragmentsByChar(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException {
      if (text1.isEmpty() && text2.isEmpty()) {
        return DiffFragment.EMPTY_ARRAY;
      }
      final DiffString side1 = text1.preappend(myVersion1.getPrevChar());
      final DiffString side2 = text2.preappend(myVersion2.getPrevChar());
      DiffFragment[] fragments = BY_CHAR.buildFragments(side1, side2);
      return Util.cutFirst(fragments);
    }

    private void addPostfixes() throws FilesTooBigForDiffException {
      DiffString postfix1 = myVersion1.getCurrentWordPostfixAndOneMore();
      DiffString postfix2 = myVersion2.getCurrentWordPostfixAndOneMore();
      int length1 = postfix1.length();
      int length2 = postfix2.length();
      DiffFragment wholePostfix = myComparisonPolicy.createFragment(postfix1, postfix2);
      if (wholePostfix.isEqual()) {
        add(DiffFragment.unchanged(cutLast(postfix1, length1), cutLast(postfix2, length2)));
        return;
      }
      if (length1 > 0 || length2 > 0) {
        DiffFragment[] fragments = BY_CHAR.buildFragments(postfix1, postfix2);
        DiffFragment firstFragment = fragments[0];
        if (firstFragment.isEqual()) {
          final DiffString text1 = cutLast(firstFragment.getText1(), length1);
          final DiffString text2 = cutLast(firstFragment.getText2(), length2);
          add(myComparisonPolicy.createFragment(text1, text2));
          //add(firstFragment);
        }
      }
    }

    @NotNull
    private static DiffString cutLast(@NotNull DiffString text, int length) {
      if (text.length() < length) return text;
      else {
        return text.substring(0, text.length() - 1);
      }
    }

    private void addOneSide(@NotNull DiffString text, @NotNull FragmentSide side) {
      DiffFragment fragment = side.createFragment(text, null, false);
      add(myComparisonPolicy.createFragment(fragment.getText1(), fragment.getText2()));
    }

    public void addChangedWords(int wordCount1, int wordCount2) {
      add(new DiffFragment(myVersion1.getWordSequence(wordCount1), myVersion2.getWordSequence(wordCount2)));
      myVersion1.incCurrentWord(wordCount1);
      myVersion2.incCurrentWord(wordCount2);
    }

    public void addTails() throws FilesTooBigForDiffException {
      DiffString tail1 = myVersion1.getNotProcessedTail();
      DiffString tail2 = myVersion2.getNotProcessedTail();
      if (tail1.isEmpty() && tail2.isEmpty()) return;
      DiffFragment[] fragments = fragmentsByChar(tail1, tail2);
      if (!myFragments.isEmpty()) {
        DiffFragment lastFragment = myFragments.get(myFragments.size() - 1);
        if (lastFragment.isChange()) {
          int oneSideCount = 0;
          while (oneSideCount < fragments.length && fragments[oneSideCount].isOneSide()) oneSideCount++;
          if (oneSideCount > 0) {
            myFragments.remove(myFragments.size() - 1);
            DiffFragment[] onesideFragments = new DiffFragment[oneSideCount];
            DiffFragment[] otherFragments = new DiffFragment[fragments.length - oneSideCount];
            System.arraycopy(fragments, 0, onesideFragments, 0, oneSideCount);
            System.arraycopy(fragments, oneSideCount, otherFragments, 0, otherFragments.length);
            DiffFragment startingOneSides = UniteSameType.uniteAll(onesideFragments);
            if (startingOneSides.isOneSide()) {
              myFragments.add(lastFragment);
              add(startingOneSides);
            } else {
              lastFragment = Util.unite(lastFragment, startingOneSides);
              myFragments.add(lastFragment);
            }
            fragments = otherFragments;
          }
        }
      }
      addAll(fragments);
    }

    public static class Version {
      @NotNull private final Word[] myWords;
      private int myCurrentWord = 0;
      private int myOffset = 0;
      @NotNull private final DiffString myText;
      @NotNull private final FragmentBuilder myBuilder;
      private final FragmentSide mySide;

      public Version(@NotNull Word[] words, @NotNull DiffString text, @NotNull FragmentBuilder builder, boolean delete) {
        myWords = words;
        myText = text;
        myBuilder = builder;
        mySide = delete ? FragmentSide.SIDE1 : FragmentSide.SIDE2;
      }

      public int getProcessedOffset() {
        return myOffset;
      }

      public int getCurrentWordIndex() {
        return myCurrentWord;
      }

      public void addOffset(int offset) {
        myOffset += offset;
      }

      public void incCurrentWord() {
        incCurrentWord(1);
      }

      @NotNull
      public DiffString getWordSequence(int wordCount) {
        int start = myWords[myCurrentWord].getStart();
        int end = myWords[myCurrentWord+wordCount-1].getEnd();
        return myText.substring(start, end);
      }

      public void incCurrentWord(int inserted) {
        myCurrentWord += inserted;
      }

      @NotNull
      public Word getCurrentWord() {
        return myWords[myCurrentWord];
      }

      @NotNull
      public DiffString getCurrentWordPrefix() {
        return getCurrentWord().getPrefix(getProcessedOffset());
      }

      @NotNull
      public DiffString getCurrentWordPostfixAndOneMore() {
        int nextStart = myCurrentWord < myWords.length - 1 ? myWords[myCurrentWord + 1].getStart() : myText.length();
        Word word = getCurrentWord();
        DiffString postfix = myText.substring(word.getEnd(), nextStart);
        return postfix.append(nextStart == myText.length() ? '\n' : myText.charAt(nextStart));
      }

      @NotNull
      public DiffString getNotProcessedTail() {
        LOG.assertTrue(myCurrentWord == myWords.length);
        return myText.substring(myOffset, myText.length());
      }

      public char getPrevChar() {
        return myOffset == 0 ? '\n' : myText.charAt(myOffset - 1);
      }

      public void addOneSide(@NotNull DiffString prefix, int wordCount) {
        if (!prefix.isEmpty()) myBuilder.addOneSide(prefix, mySide);
        myBuilder.addOneSide(getWordSequence(wordCount), mySide);
        incCurrentWord(wordCount);
      }
    }
  }
}
