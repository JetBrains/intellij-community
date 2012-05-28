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

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.popup.util.MasterDetailPopupBuilder;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.List;

public class BreakpointsMasterDetailPopupFactory {

  private final List<BreakpointPanelProvider> myBreakpointPanelProviders;
  private Project myProject;

  public BreakpointsMasterDetailPopupFactory(Project project) {
    myProject = project;
    myBreakpointPanelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : DebuggerSupport.getDebuggerSupports()) {
      myBreakpointPanelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(myBreakpointPanelProviders, new Comparator<BreakpointPanelProvider>() {
      @Override
      public int compare(BreakpointPanelProvider o1, BreakpointPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
  }

  public static BreakpointsMasterDetailPopupFactory getInstance(Project project) {
    return ServiceManager.getService(project, BreakpointsMasterDetailPopupFactory.class);
  }

  public JBPopup createPopup(@Nullable Object initialBreakpoint) {
    BreakpointMasterDetailPopupBuilder builder = new BreakpointMasterDetailPopupBuilder(myProject);
    builder.setInitialBreakpoint(initialBreakpoint);
    builder.setBreakpointsPanelProviders(myBreakpointPanelProviders);
    return builder.createPopup();
  }
}
