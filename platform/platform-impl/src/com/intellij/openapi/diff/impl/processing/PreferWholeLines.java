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
        LOG.assertTrue(false,
                       "<" + side.getText(fragment) + "> <" + side.getOtherText(nextFragment) + ">");
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
