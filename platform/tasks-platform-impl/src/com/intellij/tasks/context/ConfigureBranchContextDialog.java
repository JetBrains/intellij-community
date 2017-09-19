package com.intellij.tasks.context;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConfigureBranchContextDialog extends DialogWrapper {

  private final Project myProject;
  private JPanel myPanel;
  private JBCheckBox myReloadContext;
  private JBCheckBox myShowNotification;

  protected ConfigureBranchContextDialog(Project project) {
    super(project);
    myProject = project;
    setTitle("Branch User Interface Layout");

    myReloadContext.setSelected(VcsConfiguration.getInstance(project).RELOAD_CONTEXT);
    myReloadContext.addActionListener(e -> myShowNotification.setEnabled(myReloadContext.isSelected()));

    myShowNotification.setEnabled(myReloadContext.isSelected());
    myShowNotification.setSelected(
      NotificationsConfigurationImpl.getSettings(BranchContextTracker.NOTIFICATION.getDisplayId()).getDisplayType() !=
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
    NotificationsConfigurationImpl configuration = NotificationsConfigurationImpl.getInstanceImpl();
    configuration.changeSettings(BranchContextTracker.NOTIFICATION.getDisplayId(), displayType, true, false);

    super.doOKAction();
  }
}
