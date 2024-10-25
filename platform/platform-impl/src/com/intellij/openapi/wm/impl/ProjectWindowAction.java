// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author Bas Leijdekkers
 * This class is programmatically instantiated and registered when opening and closing projects
 * and therefore not registered in plugin.xml
 */
public final class ProjectWindowAction extends ToggleAction implements DumbAware {

  private ProjectWindowAction myPrevious;
  private ProjectWindowAction myNext;
  private final @NotNull String myProjectName;
  private final @NotNull String myProjectLocation;

  public ProjectWindowAction(@NlsSafe @NotNull String projectName, @NotNull String projectLocation, ProjectWindowAction previous) {
    super(IdeBundle.message("action.switch.project.text"));
    myProjectName = projectName;
    myProjectLocation = projectLocation;
    if (previous != null) {
      myPrevious = previous;
      myNext = previous.myNext;
      myNext.myPrevious = this;
      myPrevious.myNext = this;
    }
    else {
      myPrevious = this;
      myNext = this;
    }
    getTemplatePresentation().setText(projectName, false);
    getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.Never);
  }

  public void dispose() {
    if (myPrevious == this) {
      assert myNext == this;
      return;
    }
    if (myNext == this) {
      assert false;
      return;
    }
    myPrevious.myNext = myNext;
    myNext.myPrevious = myPrevious;
  }

  public ProjectWindowAction getPrevious() {
    return myPrevious;
  }

  public ProjectWindowAction getNext() {
    return myNext;
  }

  public @NotNull String getProjectLocation() {
    return myProjectLocation;
  }

  public @NotNull String getProjectName() {
    return myProjectName;
  }

  private @Nullable Project findProject() {
    if (LightEditService.getWindowName().equals(myProjectName)) {
      return LightEditService.getInstance().getProject();
    }
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      if (myProjectLocation.equals(project.getPresentableUrl())) {
        return project;
      }
    }
    return null;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    // show check mark for active and visible project frame
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    return myProjectLocation.equals(project.getPresentableUrl());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean selected) {
    final Project project = findProject();
    if (project == null) {
      return;
    }
    final JFrame projectFrame = WindowManager.getInstance().getFrame(project);
    if (projectFrame == null) {
      return;
    }

    final int frameState = projectFrame.getExtendedState();
    if (SystemInfo.isMac && BitUtil.isSet(projectFrame.getExtendedState(), Frame.ICONIFIED) && e.getInputEvent() instanceof KeyEvent) {
      // On Mac minimized window should not be restored this way
      return;
    }

    if (BitUtil.isSet(frameState, Frame.ICONIFIED)) {
      // restore the frame if it is minimized
      projectFrame.setExtendedState(BitUtil.set(frameState, Frame.ICONIFIED, false));
    }
    projectFrame.toFront();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      Component mostRecentFocusOwner = projectFrame.getMostRecentFocusOwner();
      if (mostRecentFocusOwner != null) {
        IdeFocusManager.getGlobalInstance().requestFocus(mostRecentFocusOwner, true);
      }
    });
  }

  @Override
  public @NonNls String toString() {
    return getTemplatePresentation().getText()
           + " previous: " + myPrevious.getTemplatePresentation().getText()
           + " next: " + myNext.getTemplatePresentation().getText();
  }
}