// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import org.jetbrains.annotations.Nullable;

public class BreakpointsDialogFactory {

  private final Project myProject;
  private Balloon myBalloonToHide;
  private Object myBreakpoint;
  private BreakpointsDialog myDialogShowing;


  public BreakpointsDialogFactory(Project project) {
    myProject = project;
  }

  public void setBalloonToHide(final Balloon balloonToHide, Object breakpoint) {
    myBalloonToHide = balloonToHide;
    myBreakpoint = breakpoint;
    Disposer.register(myBalloonToHide, new Disposable() {
      @Override
      public void dispose() {
        if (myBalloonToHide == balloonToHide) {
          myBalloonToHide = null;
          myBreakpoint = null;
        }
      }
    });
  }

  public static BreakpointsDialogFactory getInstance(Project project) {
    return ServiceManager.getService(project, BreakpointsDialogFactory.class);
  }

  public boolean popupRequested(Object breakpoint) {
    if (myBalloonToHide != null && !myBalloonToHide.isDisposed()) {
      return true;
    }
    if (myDialogShowing != null) {
      myDialogShowing.selectBreakpoint(breakpoint, true);
      myDialogShowing.toFront();
      return true;
    }
    return false;
  }

  public void showDialog(@Nullable Object initialBreakpoint) {
    if (myDialogShowing != null && myDialogShowing.getWindow().isDisplayable()) { // workaround for IDEA-197804
      myDialogShowing.selectBreakpoint(initialBreakpoint, true);
      myDialogShowing.toFront();
      return;
    }

    final BreakpointsDialog dialog = new BreakpointsDialog(myProject, initialBreakpoint != null ? initialBreakpoint : myBreakpoint, XBreakpointUtil.collectPanelProviders()) {
      @Override
      protected void dispose() {
        myBreakpoint = null;
        for (BreakpointPanelProvider provider : XBreakpointUtil.collectPanelProviders()) {
          provider.onDialogClosed(myProject);
        }
        myDialogShowing = null;

        super.dispose();
      }
    };

    if (myBalloonToHide != null) {
      if (!myBalloonToHide.isDisposed()) {
        myBalloonToHide.hide();
      }
      myBalloonToHide = null;
    }
    myDialogShowing = dialog;

    dialog.show();
  }
}
