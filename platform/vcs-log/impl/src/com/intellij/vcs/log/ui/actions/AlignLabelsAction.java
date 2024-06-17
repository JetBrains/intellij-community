// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AlignLabelsAction extends BooleanPropertyToggleAction {
  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.LABELS_LEFT_ALIGNED;
  }
}
