// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public class AlignLabelsAction extends BooleanPropertyToggleAction {

  public AlignLabelsAction() {
    super(VcsLogBundle.messagePointer("vcs.log.action.align.labels"),
          VcsLogBundle.messagePointer("vcs.log.action.description.align.labels"), null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.LABELS_LEFT_ALIGNED;
  }
}

