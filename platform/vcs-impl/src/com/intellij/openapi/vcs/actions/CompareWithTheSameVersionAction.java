package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;

public class CompareWithTheSameVersionAction extends AbstractShowDiffAction{
  @Override
  protected VcsBackgroundableActions getKey() {
    return VcsBackgroundableActions.COMPARE_WITH;
  }
}
