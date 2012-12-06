/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointMasterDetailPopupBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BreakpointsMasterDetailPopupFactory {

  private Project myProject;
  private Balloon myBalloonToHide;
  private Object myBreakpoint;
  private JBPopup myPopupShowing;


  public BreakpointsMasterDetailPopupFactory(Project project) {
    myProject = project;
  }

  public static List<BreakpointPanelProvider> collectPanelProviders() {
    List<BreakpointPanelProvider> panelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : DebuggerSupport.getDebuggerSupports()) {
      panelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(panelProviders, new Comparator<BreakpointPanelProvider>() {
      @Override
      public int compare(BreakpointPanelProvider o1, BreakpointPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
    return panelProviders;
  }

  public void setBalloonToHide(Balloon balloonToHide, Object breakpoint) {
    myBalloonToHide = balloonToHide;
    myBreakpoint = breakpoint;
  }

  public static BreakpointsMasterDetailPopupFactory getInstance(Project project) {
    return ServiceManager.getService(project, BreakpointsMasterDetailPopupFactory.class);
  }

  public boolean isBreakpointPopupShowing() {
    return (myBalloonToHide != null && !myBalloonToHide.isDisposed()) || myPopupShowing != null;
  }

  @Nullable
  public JBPopup createPopup(@Nullable Object initialBreakpoint) {
    if (myPopupShowing != null) {
      return null;
    }
    BreakpointMasterDetailPopupBuilder builder = new BreakpointMasterDetailPopupBuilder(myProject);
    builder.setInitialBreakpoint(initialBreakpoint != null ? initialBreakpoint : myBreakpoint);
    builder.setBreakpointsPanelProviders(collectPanelProviders());
    builder.setCallback(new BreakpointMasterDetailPopupBuilder.BreakpointChosenCallback() {
      @Override
      public void breakpointChosen(Project project, BreakpointItem breakpointItem, JBPopup popup, boolean withEnterOrDoubleClick) {
        if (withEnterOrDoubleClick && breakpointItem.navigate()) {
          popup.cancel();
        }
      }
    });
    myBreakpoint = null;
    final JBPopup popup = builder.createPopup();
    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        if (myBalloonToHide != null) {
          if (!myBalloonToHide.isDisposed()) {
            myBalloonToHide.hide();
          }
          myBalloonToHide = null;
        }
        myPopupShowing = popup;
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        for (BreakpointPanelProvider provider : collectPanelProviders()) {
          provider.onDialogClosed(myProject);
        }
        myPopupShowing = null;
      }
    });
    return popup;
  }
}
