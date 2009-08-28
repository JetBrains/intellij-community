package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffUtil;

public interface DiffPolicy {
  DiffFragment[] buildFragments(String text1, String text2);

  DiffPolicy LINES_WO_FORMATTING = new LineBlocks(ComparisonPolicy.IGNORE_SPACE);
  DiffPolicy DEFAULT_LINES = new LineBlocks(ComparisonPolicy.DEFAULT);

  class LineBlocks implements DiffPolicy {
    private final ComparisonPolicy myComparisonPolicy;

    public LineBlocks(ComparisonPolicy comparisonPolicy) {
      myComparisonPolicy = comparisonPolicy;
    }

    public DiffFragment[] buildFragments(String text1, String text2) {
      String[] strings1 = DiffUtil.convertToLines(text1);
      String[] strings2 = DiffUtil.convertToLines(text2);
      return myComparisonPolicy.buildDiffFragmentsFromLines(strings1, strings2);
    }

  }

  class ByChar implements DiffPolicy {
    private final ComparisonPolicy myComparisonPolicy;

    public ByChar(ComparisonPolicy comparisonPolicy) {
      myComparisonPolicy = comparisonPolicy;
    }

    public DiffFragment[] buildFragments(String text1, String text2) {
      return myComparisonPolicy.buildFragments(splitByChar(text1), splitByChar(text2));
    }

    private String[] splitByChar(String text) {
      String[] result = new String[text.length()];
      for (int i = 0; i < result.length; i++) {
        result[i] = text.substring(i, i + 1);
      }
      return result;
    }
  }

}
