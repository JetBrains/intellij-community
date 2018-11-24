/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
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
  public FList<TextRange> matchingFragments(@NotNull String name) {
    FList<TextRange> match = super.matchingFragments(name);
    if (!ContainerUtil.isEmpty(match)) {
      List<FList<TextRange>> allMatchesReversed = new ArrayList<FList<TextRange>>();
      int lastOffset = 0;
      while (!ContainerUtil.isEmpty(match)) {
        FList<TextRange> reversedWithAbsoluteOffsets = FList.emptyList();
        for (TextRange r : match) {
          reversedWithAbsoluteOffsets = reversedWithAbsoluteOffsets.prepend(r.shiftRight(lastOffset));
        }
        allMatchesReversed.add(reversedWithAbsoluteOffsets);
        lastOffset = reversedWithAbsoluteOffsets.get(0).getEndOffset();
        match = super.matchingFragments(name.substring(lastOffset));
        
      }
      match = FList.emptyList();
      for (int i = allMatchesReversed.size() - 1; i >= 0; i--) {
        for (TextRange range : allMatchesReversed.get(i)) {
          match = match.prepend(range);
        }
      }
    }
    return match;
  }
}
