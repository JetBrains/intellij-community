/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public boolean isBreakpointPopupShowing() {
    return (myBalloonToHide != null && !myBalloonToHide.isDisposed()) || myDialogShowing != null;
  }

  public void showDialog(@Nullable Object initialBreakpoint) {
    if (myDialogShowing != null) {
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
