// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.ByWordRt.InlineChunk;
import com.intellij.diff.comparison.ByWordRt.NewlineChunk;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/*
 * Given matchings on words, split initial line block into 'logically different' line blocks
 */
class LineFragmentSplitter {
  @NotNull private final CharSequence myText1;
  @NotNull private final CharSequence myText2;

  @NotNull private final List<? extends InlineChunk> myWords1;
  @NotNull private final List<? extends InlineChunk> myWords2;
  @NotNull private final FairDiffIterable myIterable;
  @NotNull private final CancellationChecker myIndicator;

  @NotNull private final List<WordBlock> myResult = new ArrayList<>();

  LineFragmentSplitter(@NotNull CharSequence text1,
                       @NotNull CharSequence text2,
                       @NotNull List<? extends InlineChunk> words1,
                       @NotNull List<? extends InlineChunk> words2,
                       @NotNull FairDiffIterable iterable,
                       @NotNull CancellationChecker indicator) {
    myText1 = text1;
    myText2 = text2;
    myWords1 = words1;
    myWords2 = words2;
    myIterable = iterable;
    myIndicator = indicator;
  }

  private int last1 = -1;
  private int last2 = -1;
  private PendingChunk pendingChunk = null;

  // indexes here are a bit tricky
  // -1 - the beginning of file, words.size() - end of file, everything in between - InlineChunks (words or newlines)

  @NotNull
  public List<WordBlock> run() {
    boolean hasEqualWords = false;
    for (Range range : myIterable.iterateUnchanged()) {
      int count = range.end1 - range.start1;
      for (int i = 0; i < count; i++) {
        int index1 = range.start1 + i;
        int index2 = range.start2 + i;

        if (isNewline(myWords1, index1) && isNewline(myWords2, index2)) { // split by matched newlines
          addLineChunk(index1, index2, hasEqualWords);
          hasEqualWords = false;
        }
        else {
          if (isFirstInLine(myWords1, index1) && isFirstInLine(myWords2, index2)) { // split by matched first word in line
            addLineChunk(index1 - 1, index2 - 1, hasEqualWords);
            hasEqualWords = false;
          }
          // TODO: split by 'last word in line' + 'last word in whole sequence' ?
          hasEqualWords = true;
        }
      }
    }
    addLineChunk(myWords1.size(), myWords2.size(), hasEqualWords);

    if (pendingChunk != null) myResult.add(pendingChunk.block);

    return myResult;
  }

  private void addLineChunk(int end1, int end2, boolean hasEqualWords) {
    if (last1 > end1 || last2 > end2) return;

    PendingChunk chunk = createChunk(last1, last2, end1, end2, hasEqualWords);
    if (chunk.block.offsets.isEmpty()) return;

    if (pendingChunk != null && shouldMergeChunks(pendingChunk, chunk)) {
      pendingChunk = mergeChunks(pendingChunk, chunk);
    }
    else {
      if (pendingChunk != null) myResult.add(pendingChunk.block);
      pendingChunk = chunk;
    }

    last1 = end1;
    last2 = end2;
  }

  @NotNull
  private PendingChunk createChunk(int start1, int start2, int end1, int end2, boolean hasEqualWords) {
    int startOffset1 = getOffset(myWords1, myText1, start1);
    int startOffset2 = getOffset(myWords2, myText2, start2);
    int endOffset1 = getOffset(myWords1, myText1, end1);
    int endOffset2 = getOffset(myWords2, myText2, end2);

    start1 = Math.max(0, start1 + 1);
    start2 = Math.max(0, start2 + 1);
    end1 = Math.min(end1 + 1, myWords1.size());
    end2 = Math.min(end2 + 1, myWords2.size());

    WordBlock block = new WordBlock(new Range(start1, end1, start2, end2), new Range(startOffset1, endOffset1, startOffset2, endOffset2));

    return new PendingChunk(block, hasEqualWords, hasWordsInside(block), isEqualsIgnoreWhitespace(block));
  }

  private static boolean shouldMergeChunks(@NotNull PendingChunk chunk1, @NotNull PendingChunk chunk2) {
    if (!chunk1.hasEqualWords && !chunk2.hasEqualWords) return true; // combine lines, that matched only by '\n'
    if (chunk1.isEqualIgnoreWhitespaces && chunk2.isEqualIgnoreWhitespaces) return true; // combine whitespace-only changed lines
    if (!chunk1.hasWordsInside || !chunk2.hasWordsInside) return true; // squash block without words in it
    return false;
  }

  @NotNull
  private static PendingChunk mergeChunks(@NotNull PendingChunk chunk1, @NotNull PendingChunk chunk2) {
    WordBlock block1 = chunk1.block;
    WordBlock block2 = chunk2.block;
    WordBlock newBlock = new WordBlock(new Range(block1.words.start1, block2.words.end1, block1.words.start2, block2.words.end2),
                                       new Range(block1.offsets.start1, block2.offsets.end1, block1.offsets.start2, block2.offsets.end2));
    return new PendingChunk(newBlock,
                            chunk1.hasEqualWords || chunk2.hasEqualWords,
                            chunk1.hasWordsInside || chunk2.hasWordsInside,
                            chunk1.isEqualIgnoreWhitespaces && chunk2.isEqualIgnoreWhitespaces);
  }

  private boolean isEqualsIgnoreWhitespace(@NotNull WordBlock block) {
    CharSequence sequence1 = myText1.subSequence(block.offsets.start1, block.offsets.end1);
    CharSequence sequence2 = myText2.subSequence(block.offsets.start2, block.offsets.end2);
    return ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.IGNORE_WHITESPACES);
  }

  private boolean hasWordsInside(@NotNull WordBlock block) {
    for (int i = block.words.start1; i < block.words.end1; i++) {
      if (!(myWords1.get(i) instanceof NewlineChunk)) return true;
    }
    for (int i = block.words.start2; i < block.words.end2; i++) {
      if (!(myWords2.get(i) instanceof NewlineChunk)) return true;
    }
    return false;
  }

  private static int getOffset(@NotNull List<? extends InlineChunk> words, @NotNull CharSequence text, int index) {
    if (index == -1) return 0;
    if (index == words.size()) return text.length();
    InlineChunk chunk = words.get(index);
    assert chunk instanceof NewlineChunk;
    return chunk.getOffset2();
  }

  private static boolean isNewline(@NotNull List<? extends InlineChunk> words1, int index) {
    return words1.get(index) instanceof NewlineChunk;
  }

  private static boolean isFirstInLine(@NotNull List<? extends InlineChunk> words1, int index) {
    if (index == 0) return true;
    return words1.get(index - 1) instanceof NewlineChunk;
  }

  //
  // Helpers
  //

  public static class WordBlock {
    @NotNull public final Range words;
    @NotNull public final Range offsets;

    public WordBlock(@NotNull Range words, @NotNull Range offsets) {
      this.words = words;
      this.offsets = offsets;
    }
  }

  private static class PendingChunk {
    @NotNull public final WordBlock block;
    public final boolean hasEqualWords;
    public final boolean hasWordsInside;
    public final boolean isEqualIgnoreWhitespaces;

    PendingChunk(@NotNull WordBlock block, boolean hasEqualWords, boolean hasWordsInside, boolean isEqualIgnoreWhitespaces) {
      this.block = block;
      this.hasEqualWords = hasEqualWords;
      this.hasWordsInside = hasWordsInside;
      this.isEqualIgnoreWhitespaces = isEqualIgnoreWhitespaces;
    }
  }
}
