// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link VcsLogInternalDataKeys#MAIN_UI}
 */
public class MainVcsLogUiDataRule implements GetDataRule {
  @Nullable
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    VcsLogUi ui = VcsLogDataKeys.VCS_LOG_UI.getData(dataProvider);
    if (ui instanceof MainVcsLogUi) {
      return ui;
    }
    return null;
  }
}
