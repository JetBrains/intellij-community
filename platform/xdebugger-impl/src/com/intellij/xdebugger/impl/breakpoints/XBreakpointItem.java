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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;

import javax.swing.*;

/**
* Created with IntelliJ IDEA.
* User: intendia
* Date: 10.05.12
* Time: 1:14
* To change this template use File | Settings | File Templates.
*/
class XBreakpointItem implements BreakpointItem {
  private final XBreakpoint<?> myBreakpoint;

  public XBreakpointItem(XBreakpoint<?> breakpoint) {
    myBreakpoint = breakpoint;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    renderer.setIcon(((XBreakpointBase)myBreakpoint).getIcon());
    renderer.append(XBreakpointUtil.getDisplayText(myBreakpoint));
  }

  @Override
  public void updateMnemonicLabel(JLabel label) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void execute(Project project) {
    final XSourcePosition position = myBreakpoint.getSourcePosition();
    if (position != null) {
      position.createNavigatable(project).navigate(true);
    }
  }

  @Override
  public String speedSearchText() {
    return ((XBreakpointBase)myBreakpoint).getType().getDisplayText(myBreakpoint);
  }

  @Override
  public String footerText() {
    return ((XBreakpointBase)myBreakpoint).getType().getDisplayText(myBreakpoint);
  }

  @Override
  public void updateDetailView(DetailView panel) {
    Project project = ((XBreakpointBase)myBreakpoint).getProject();

    XLightBreakpointPropertiesPanel<XBreakpoint<?>> propertiesPanel =
      new XLightBreakpointPropertiesPanel<XBreakpoint<?>>(project, getManager(), myBreakpoint, true);
    propertiesPanel.loadProperties();
    panel.setDetailPanel(propertiesPanel.getMainPanel());

    XSourcePosition sourcePosition = myBreakpoint.getSourcePosition();
    if (sourcePosition != null) {
      panel.navigateInPreviewEditor(sourcePosition.getFile(), new LogicalPosition(sourcePosition.getLine(), sourcePosition.getOffset()));
    } else {
      panel.clearEditor();
    }
  }

  private XBreakpointManagerImpl getManager() {
    return ((XBreakpointBase)myBreakpoint).getBreakpointManager();
  }

  @Override
  public boolean allowedToRemove() {
    return !getManager().isDefaultBreakpoint(myBreakpoint);
  }

  @Override
  public void removed(Project project) {
    final XBreakpointManagerImpl breakpointManager = getManager();
    new WriteAction() {
      protected void run(final Result result) {
        breakpointManager.removeBreakpoint(myBreakpoint);
      }
    }.execute();

  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }
}
