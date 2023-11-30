// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GitVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {

  public GitVersionFilterComponent(boolean showDateFilter) {
    super(showDateFilter);
    init(new ChangeBrowserSettings());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return (JComponent)getStandardPanel();
  }
}
