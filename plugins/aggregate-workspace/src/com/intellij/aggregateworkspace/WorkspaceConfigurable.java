package com.intellij.aggregateworkspace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class WorkspaceConfigurable implements Configurable {
  private final Project project;
  private WorkspaceSettingsPanel panel;

  public WorkspaceConfigurable(Project project) {
    this.project = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Workspace Shell";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    panel = new WorkspaceSettingsPanel(project);
    return panel;
  }

  @Override
  public boolean isModified() {
    return panel != null && panel.isModified();
  }

  @Override
  public void apply() {
    if (panel != null) {
      panel.apply();
    }
  }

  @Override
  public void reset() {
    if (panel != null) {
      panel.reset();
    }
  }
}
