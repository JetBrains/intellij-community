// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public interface DiffPolicy {
  DiffFragment @NotNull [] buildFragments(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException;

  @TestOnly
  DiffFragment @NotNull [] buildFragments(@NotNull String text1, @NotNull String text2) throws FilesTooBigForDiffException;

  DiffPolicy LINES_WO_FORMATTING = new LineBlocks(ComparisonPolicy.IGNORE_SPACE);
  DiffPolicy DEFAULT_LINES = new LineBlocks(ComparisonPolicy.DEFAULT);

  class LineBlocks implements DiffPolicy {
    private final ComparisonPolicy myComparisonPolicy;

    public LineBlocks(ComparisonPolicy comparisonPolicy) {
      myComparisonPolicy = comparisonPolicy;
    }

    @Override
    @TestOnly
    public DiffFragment @NotNull [] buildFragments(@NotNull String text1, @NotNull String text2) throws FilesTooBigForDiffException {
      return buildFragments(DiffString.create(text1), DiffString.create(text2));
    }

    @Override
    public DiffFragment @NotNull [] buildFragments(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException {
      DiffString[] strings1 = text1.tokenize();
      DiffString[] strings2 = text2.tokenize();
      return myComparisonPolicy.buildDiffFragmentsFromLines(strings1, strings2);
    }

  }

  class ByChar implements DiffPolicy {
    private final ComparisonPolicy myComparisonPolicy;

    public ByChar(ComparisonPolicy comparisonPolicy) {
      myComparisonPolicy = comparisonPolicy;
    }

    @Override
    @TestOnly
    public DiffFragment @NotNull [] buildFragments(@NotNull String text1, @NotNull String text2) throws FilesTooBigForDiffException {
      return buildFragments(DiffString.create(text1), DiffString.create(text2));
    }

    @Override
    public DiffFragment @NotNull [] buildFragments(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException {
      return myComparisonPolicy.buildFragments(splitByChar(text1), splitByChar(text2));
    }

    private static DiffString[] splitByChar(@NotNull DiffString text) {
      DiffString[] result = new DiffString[text.length()];
      for (int i = 0; i < result.length; i++) {
        result[i] = text.substring(i, i + 1);
      }
      return result;
    }
  }

}
