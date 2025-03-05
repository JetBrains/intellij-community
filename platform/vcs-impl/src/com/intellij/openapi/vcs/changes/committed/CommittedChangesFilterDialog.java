// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.EdtScheduler;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class CommittedChangesFilterDialog extends DialogWrapper {
  private final ChangesBrowserSettingsEditor panel;
  private ChangeBrowserSettings settings;
  private final JLabel myErrorLabel = new JLabel();
  private Job validateAlarm = null;
  private final Runnable validateRunnable = () -> {
    validateInput();
    scheduleValidation();
  };

  private void scheduleValidation() {
    validateAlarm = EdtScheduler.getInstance().schedule(500, ModalityState.stateForComponent(panel.getComponent()), validateRunnable);
  }

  public CommittedChangesFilterDialog(Project project,
                                      @NotNull ChangesBrowserSettingsEditor panel,
                                      @NotNull ChangeBrowserSettings settings) {
    super(project, false);
    this.panel = panel;
    //noinspection unchecked
    this.panel.setSettings(settings);
    setTitle(VcsBundle.message("browse.changes.filter.title"));
    init();
    myErrorLabel.setForeground(JBColor.RED);
    validateInput();
    scheduleValidation();
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(this.panel.getComponent(), BorderLayout.CENTER);
    panel.add(myErrorLabel, BorderLayout.SOUTH);
    return panel;
  }

  private void validateInput() {
    String error = panel.validateInput();
    setOKActionEnabled(error == null);
    myErrorLabel.setText(error == null ? " " : error);
  }

  @Override
  protected void doOKAction() {
    validateInput();
    if (isOKActionEnabled()) {
      validateAlarm.cancel(null);
      settings = panel.getSettings();
      super.doOKAction();
    }
  }

  public ChangeBrowserSettings getSettings() {
    return settings;
  }

  @Override
  protected @NonNls String getDimensionServiceKey() {
    return "AbstractVcsHelper.FilterDialog." + panel.getDimensionServiceKey();
  }
}
