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
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.text.StringUtil;

class PreferWholeLines implements DiffCorrection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.PreferWholeLines");
  public static final DiffCorrection INSTANCE = new PreferWholeLines();
  public DiffFragment[] correct(DiffFragment[] fragments) {
    for (int i = 1; i < fragments.length - 1; i++) {
      DiffFragment fragment = fragments[i];
      if (!fragment.isOneSide()) continue;
      DiffFragment nextFragment = fragments[i + 1];
      FragmentSide side = FragmentSide.chooseSide(fragment);
      if (nextFragment.isOneSide()) {
        LOG.error("<" + side.getText(fragment) + "> <" + side.getOtherText(nextFragment) + ">");
      }
      if (StringUtil.startsWithChar(side.getText(fragment), '\n') &&
          StringUtil.startsWithChar(side.getText(nextFragment), '\n') &&
          StringUtil.startsWithChar(side.getOtherText(nextFragment), '\n')) {
        DiffFragment previous = fragments[i - 1];
        previous = side.createFragment(side.getText(previous) + "\n",
                                       side.getOtherText(previous) + "\n",
                                       previous.isModified());
        fragments[i - 1] = previous;
        fragment = side.createFragment(side.getText(fragment).substring(1) + "\n",
                                       side.getOtherText(fragment),
                                       fragment.isModified());
        fragments[i] = fragment;
        nextFragment = side.createFragment(side.getText(nextFragment).substring(1),
                                           side.getOtherText(nextFragment).substring(1),
                                           nextFragment.isModified());
        fragments[i + 1] = nextFragment;
      }
    }
    return fragments;
  }
}
