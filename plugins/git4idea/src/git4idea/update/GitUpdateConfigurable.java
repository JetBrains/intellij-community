// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import com.intellij.openapi.options.Configurable;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GitUpdateConfigurable implements Configurable {
  private final GitVcsSettings mySettings;
  private GitUpdateOptionsPanel myPanel;

  public GitUpdateConfigurable(@NotNull GitVcsSettings settings) {
    mySettings = settings;
  }

  @Override
  public @Nls String getDisplayName() {
    return GitBundle.message("update.options.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.VersionControl.Git.UpdateProject";
  }

  @Override
  public JComponent createComponent() {
    myPanel = new GitUpdateOptionsPanel(mySettings);
    return myPanel.getPanel();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() {
    myPanel.applyTo();
  }

  @Override
  public void reset() {
    myPanel.updateFrom();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }
}
