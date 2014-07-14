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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author irengrig
 *         Date: 7/7/11
 *         Time: 12:49 PM
 */
public class PresetBlocksDiffPolicy implements DiffPolicy {
  // fragment _start_ offsets
  private List<BeforeAfter<TextRange>> myRanges;
  @NotNull private final DiffPolicy myDelegate;

  public PresetBlocksDiffPolicy(@NotNull DiffPolicy delegate) {
    myDelegate = delegate;
  }

  @TestOnly
  @NotNull
  @Override
  public DiffFragment[] buildFragments(@NotNull String text1, @NotNull String text2) throws FilesTooBigForDiffException {
    return buildFragments(DiffString.create(text1), DiffString.create(text2));
  }

  @NotNull
  @Override
  public DiffFragment[] buildFragments(@NotNull DiffString text1, @NotNull DiffString text2) throws FilesTooBigForDiffException {
    final List<DiffFragment> fragments = new ArrayList<DiffFragment>();
    for (int i = 0; i < myRanges.size(); i++) {
      final BeforeAfter<TextRange> range = myRanges.get(i);
      fragments.addAll(Arrays.asList(myDelegate.buildFragments(
        text1.substring(range.getBefore().getStartOffset(), range.getBefore().getEndOffset()).copy(),
        text2.substring(range.getAfter().getStartOffset(), range.getAfter().getEndOffset()).copy())));
    }

    return fragments.toArray(new DiffFragment[fragments.size()]);
  }

  public void setRanges(List<BeforeAfter<TextRange>> ranges) {
    myRanges = ranges;
  }
}
