// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;

import javax.swing.*;

/**
 * The component for version filter.
 */
public class HgVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {

  public HgVersionFilterComponent(boolean showDateFilter) {
    super(showDateFilter);
    init(new ChangeBrowserSettings());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JComponent getComponent() {
    return (JComponent)getStandardPanel();
  }
}