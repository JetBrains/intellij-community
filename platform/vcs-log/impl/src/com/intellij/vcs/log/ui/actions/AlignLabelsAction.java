// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public class AlignLabelsAction extends BooleanPropertyToggleAction {

  public AlignLabelsAction() {
    super("Align References to the Left", "Show references on the left of commit message", null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return MainVcsLogUiProperties.LABELS_LEFT_ALIGNED;
  }
}

