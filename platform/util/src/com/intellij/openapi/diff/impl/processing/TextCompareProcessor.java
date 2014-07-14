/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.LineBlockDivider;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextCompareProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.Processor");
  @NotNull private final ComparisonPolicy myComparisonPolicy;
  @NotNull private final DiffPolicy myDiffPolicy;
  @NotNull private final HighlightMode myHighlightMode;

  public TextCompareProcessor(@NotNull ComparisonPolicy comparisonPolicy,
                              @NotNull DiffPolicy diffPolicy,
                              @NotNull HighlightMode highlightMode) {
    myComparisonPolicy = comparisonPolicy;
    myDiffPolicy = diffPolicy;
    myHighlightMode = highlightMode;
  }

  public TextCompareProcessor(@NotNull ComparisonPolicy comparisonPolicy) {
    this(comparisonPolicy, DiffPolicy.LINES_WO_FORMATTING, HighlightMode.BY_WORD);
  }

  public List<LineFragment> process(@Nullable String text1, @Nullable String text2) throws FilesTooBigForDiffException {
    if (myHighlightMode == HighlightMode.NO_HIGHLIGHTING) {
      return Collections.emptyList();
    }

    text1 = StringUtil.notNullize(text1);
    text2 = StringUtil.notNullize(text2);
    if (text1.isEmpty() || text2.isEmpty()) {
      return new DummyDiffFragmentsProcessor().process(text1, text2);
    }

    DiffString diffText1 = DiffString.create(text1);
    DiffString diffText2 = DiffString.create(text2);

    DiffFragment[] woFormattingBlocks = myDiffPolicy.buildFragments(diffText1, diffText2);
    DiffFragment[] step1lineFragments = new DiffCorrection.TrueLineBlocks(myComparisonPolicy).correctAndNormalize(woFormattingBlocks);
    ArrayList<LineFragment> lineBlocks = new DiffFragmentsProcessor().process(step1lineFragments);

    int badLinesCount = 0;
    if (myHighlightMode == HighlightMode.BY_WORD) {
      for (LineFragment lineBlock : lineBlocks) {
        if (lineBlock.isOneSide() || lineBlock.isEqual()) continue;
        try {
          DiffString subText1 = lineBlock.getText(diffText1, FragmentSide.SIDE1);
          DiffString subText2 = lineBlock.getText(diffText2, FragmentSide.SIDE2);
          ArrayList<LineFragment> subFragments = findSubFragments(subText1, subText2);
          lineBlock.setChildren(new ArrayList<Fragment>(subFragments));
          lineBlock.adjustTypeFromChildrenTypes();
        }
        catch (FilesTooBigForDiffException ignore) {
          // If we can't by-word compare two lines - this is not a reason to break entire diff.
          badLinesCount++;
          if (badLinesCount > FilesTooBigForDiffException.MAX_BAD_LINES) break;
        }
      }
    }
    return lineBlocks;
  }

  private ArrayList<LineFragment> findSubFragments(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException {
    DiffFragment[] fragments = new ByWord(myComparisonPolicy).buildFragments(text1, text2);
    fragments = DiffCorrection.ConnectSingleSideToChange.INSTANCE.correct(fragments);
    fragments = UniteSameType.INSTANCE.correct(fragments);
    fragments = PreferWholeLines.INSTANCE.correct(fragments);
    fragments = UniteSameType.INSTANCE.correct(fragments);
    DiffFragment[][] lines = Util.splitByUnchangedLines(fragments);
    lines = Util.uniteFormattingOnly(lines);

    LineFragmentsCollector collector = new LineFragmentsCollector();
    for (DiffFragment[] line : lines) {
      DiffFragment[][] subLines = LineBlockDivider.SINGLE_SIDE.divide(line);
      subLines = Util.uniteFormattingOnly(subLines);
      for (DiffFragment[] subLineFragments : subLines) {
        LineFragment subLine = collector.addDiffFragment(Util.concatenate(subLineFragments));
        if (!subLine.isOneSide()) {
          subLine.setChildren(processInlineFragments(subLineFragments));
        }
      }
    }
    return collector.getFragments();
  }

  private static ArrayList<Fragment> processInlineFragments(DiffFragment[] subLineFragments) {
    LOG.assertTrue(subLineFragments.length > 0);
    FragmentsCollector result = new FragmentsCollector();
    for (DiffFragment fragment : subLineFragments) {
      result.addDiffFragment(fragment);
    }
    return result.getFragments();
  }
}
