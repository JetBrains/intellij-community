// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.tasks.TaskBundle;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public class ConfigureBranchContextDialog extends DialogWrapper {
  private final Project myProject;
  private JPanel myPanel;
  private JBCheckBox myReloadContext;
  private JBCheckBox myShowNotification;

  protected ConfigureBranchContextDialog(Project project) {
    super(project);
    myProject = project;
    setTitle(TaskBundle.message("branch.workspace.settings"));

    myReloadContext.setSelected(VcsConfiguration.getInstance(project).RELOAD_CONTEXT);
    myReloadContext.addActionListener(e -> myShowNotification.setEnabled(myReloadContext.isSelected()));

    myShowNotification.setEnabled(myReloadContext.isSelected());
    myShowNotification.setSelected(
      NotificationsConfiguration.getNotificationsConfiguration().getDisplayType(BranchContextTracker.NOTIFICATION.getDisplayId()) !=
      NotificationDisplayType.NONE);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    VcsConfiguration.getInstance(myProject).RELOAD_CONTEXT = myReloadContext.isSelected();

    NotificationDisplayType displayType = myShowNotification.isSelected() ? NotificationDisplayType.BALLOON : NotificationDisplayType.NONE;
    NotificationsConfiguration.getNotificationsConfiguration().changeSettings(BranchContextTracker.NOTIFICATION.getDisplayId(), displayType, true, false);

    super.doOKAction();
  }
}
