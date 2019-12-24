// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class HgVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {

  public HgVersionFilterComponent(boolean showDateFilter) {
    super(showDateFilter);
    init(new ChangeBrowserSettings());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return (JComponent)getStandardPanel();
  }
}