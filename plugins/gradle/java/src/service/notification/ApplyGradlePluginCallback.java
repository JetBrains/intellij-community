// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.codeInsight.actions.AddGradleDslPluginAction;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Vladislav.Soroka
 */
public class ApplyGradlePluginCallback extends NotificationListener.Adapter {

  public static final String ID = "apply_gradle_plugin";

  private final NotificationData myNotificationData;
  private final Project myProject;

  public ApplyGradlePluginCallback(NotificationData notificationData, Project project) {
    myNotificationData = notificationData;
    myProject = project;
  }

  @Override
  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
    GradleNotificationCallbackUtil.navigateByNotificationData(myProject, myNotificationData);

    final AnAction action = ActionManager.getInstance().getAction(AddGradleDslPluginAction.ID);
    assert action instanceof AddGradleDslPluginAction;
    final AddGradleDslPluginAction addGradleDslPluginAction = (AddGradleDslPluginAction)action;
    ActionManager.getInstance().tryToExecute(
      addGradleDslPluginAction, ActionCommand.getInputEvent(AddGradleDslPluginAction.ID), null, ActionPlaces.UNKNOWN, true);
  }
}
