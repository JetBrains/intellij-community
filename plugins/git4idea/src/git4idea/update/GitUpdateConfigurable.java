// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nls
  public String getDisplayName() {
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
