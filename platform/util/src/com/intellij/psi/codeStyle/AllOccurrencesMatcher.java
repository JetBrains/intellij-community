// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link FixingLayoutMatcher} extension that returns all matches (not just the first one) 
 * from {@link MinusculeMatcher#matchingFragments(String)}.
 */
public class AllOccurrencesMatcher extends FixingLayoutMatcher {
  public AllOccurrencesMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, String hardSeparators) {
    super(pattern, options, hardSeparators);
  }

  @Nullable
  @Override
  public FList<Range> matchingFragments(@NotNull String name) {
    FList<Range> match = super.matchingFragments(name);
    if (!ContainerUtil.isEmpty(match)) {
      List<FList<Range>> allMatchesReversed = new ArrayList<FList<Range>>();
      int lastOffset = 0;
      while (!ContainerUtil.isEmpty(match)) {
        FList<Range> reversedWithAbsoluteOffsets = FList.emptyList();
        for (Range r : match) {
          reversedWithAbsoluteOffsets = reversedWithAbsoluteOffsets.prepend(r.shiftRight(lastOffset));
        }
        allMatchesReversed.add(reversedWithAbsoluteOffsets);
        lastOffset = reversedWithAbsoluteOffsets.get(0).getEndOffset();
        match = super.matchingFragments(name.substring(lastOffset));
        
      }
      match = FList.emptyList();
      for (int i = allMatchesReversed.size() - 1; i >= 0; i--) {
        for (Range range : allMatchesReversed.get(i)) {
          match = match.prepend(range);
        }
      }
    }
    return match;
  }
}
