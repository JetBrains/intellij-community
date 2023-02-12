// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.focus;

import com.intellij.internal.InternalActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.intellij.openapi.wm.impl.FocusRequestInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class FocusTracesAction extends AnAction implements DumbAware {

  private static boolean myActive = false;
  private AWTEventListener myFocusTracker;

  FocusTracesAction() {
    setEnabledInModalContext(true);
  }

  public static boolean isActive() {
    return myActive;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final IdeFocusManager manager = IdeFocusManager.getGlobalInstance();
    if (!(manager instanceof FocusManagerImpl focusManager)) {
      return;
    }

    myActive = !myActive;
    if (myActive) {
      myFocusTracker = new AWTEventListener() {
        @Override
        public void eventDispatched(AWTEvent event) {
          if (event instanceof FocusEvent && event.getID() == FocusEvent.FOCUS_GAINED) {
            focusManager.recordFocusRequest(((FocusEvent)event).getComponent(), false);
          }
        }
      };
      Toolkit.getDefaultToolkit().addAWTEventListener(myFocusTracker, AWTEvent.FOCUS_EVENT_MASK);
    }

    if (!myActive) {
      final List<FocusRequestInfo> requests = focusManager.getRequests();
      new FocusTracesDialog(project, new ArrayList<>(requests)).show();
      Toolkit.getDefaultToolkit().removeAWTEventListener(myFocusTracker);
      myFocusTracker = null;
      requests.clear();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setText(myActive ?
                         InternalActionsBundle.messagePointer("action.presentation.FocusTracesAction.text.stop.focus.tracing") :
                         InternalActionsBundle.messagePointer("action.presentation.FocusTracesAction.text.start.focus.tracing"));
    presentation.setEnabledAndVisible(e.getProject() != null);
  }
}
