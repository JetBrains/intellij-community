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
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

class UniteSameType implements DiffCorrection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.UniteSameType");
  public static final DiffCorrection INSTANCE = new UniteSameType();
  @Override
  public DiffFragment[] correct(DiffFragment[] fragments) {
    return unitSameTypes(covertSequentialOneSideToChange(unitSameTypes(fragments)));
  }

  @NotNull
  private static DiffFragment[] unitSameTypes(@NotNull DiffFragment[] fragments) {
    if (fragments.length < 2) return fragments;
    DiffCorrection.FragmentsCollector collector = new DiffCorrection.FragmentsCollector();
    DiffFragment previous = fragments[0];
    for (int i = 1; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (!fragment.isOneSide() && fragment.getText1().isEmpty() && fragment.getText2().isEmpty()) continue;
      if (Util.isSameType(previous, fragment)) {
        previous = Util.unite(previous, fragment);
      } else {
        collector.add(previous);
        previous = fragment;
      }
    }
    collector.add(previous);
    return collector.toArray();
  }

  @NotNull
  private static DiffFragment[] covertSequentialOneSideToChange(@NotNull DiffFragment[] fragments) {
    if (fragments.length < 2) return fragments;
    DiffCorrection.FragmentsCollector collector = new DiffCorrection.FragmentsCollector();
//    DiffFragment previous = fragments[0];
    DiffFragment previous = null;
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (fragment.isOneSide()) {
        if (previous == null) previous = fragment;
        else {
          FragmentSide side = FragmentSide.chooseSide(fragment);
          DiffString previousText = side.getText(previous);
          if (previousText == null) previousText = DiffString.EMPTY;
          previous = side.createFragment(DiffString.concatenateNullable(previousText, side.getText(fragment)),
                                         side.getOtherText(previous), true);
        }
      } else {
        if (previous != null) collector.add(previous);
        previous = null;
        collector.add(fragment);
      }
    }
    if (previous != null) collector.add(previous);
    return collector.toArray();
  }

  public static DiffFragment uniteAll(DiffFragment[] fragments) throws FilesTooBigForDiffException {
    fragments = INSTANCE.correct(fragments);
    LOG.assertTrue(fragments.length == 1);
    return fragments[0];
  }
}
