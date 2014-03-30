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
import com.intellij.openapi.diff.impl.string.DiffString;
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

      DiffString fragmentText = side.getText(fragment);
      DiffString otherNextFragmentText = side.getOtherText(nextFragment);
      DiffString nextFragmentText = side.getText(nextFragment);

      if (nextFragment.isOneSide()) {
        LOG.error("<" + fragmentText + "> <" + otherNextFragmentText + ">");
      }
      if (StringUtil.startsWithChar(fragmentText, '\n') &&
          StringUtil.startsWithChar(nextFragmentText, '\n') &&
          StringUtil.startsWithChar(otherNextFragmentText, '\n')) {

        DiffFragment previous = fragments[i - 1];
        DiffString previousText = side.getText(previous);
        DiffString otherPreciousText = side.getOtherText(previous);

        assert previous != null;
        assert previousText != null;
        assert otherPreciousText != null;
        assert fragmentText != null;
        assert nextFragmentText != null;
        assert otherNextFragmentText != null;

        previous = side.createFragment(previousText.append('\n'), otherPreciousText.append('\n'), previous.isModified());
        fragments[i - 1] = previous;
        fragment = side.createFragment(fragmentText.substring(1).append('\n'), side.getOtherText(fragment), fragment.isModified());
        fragments[i] = fragment;
        nextFragment = side.createFragment(nextFragmentText.substring(1), otherNextFragmentText.substring(1), nextFragment.isModified());
        fragments[i + 1] = nextFragment;
      }
    }
    return fragments;
  }
}
