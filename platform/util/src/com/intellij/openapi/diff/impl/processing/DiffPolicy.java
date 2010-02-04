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

import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;

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
      String[] strings1 = new LineTokenizer(text1).execute();
      String[] strings2 = new LineTokenizer(text2).execute();
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
