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

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPropertiesPanel;

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
  }

  @Override
  public void updateMnemonicLabel(JLabel label) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void execute(Project project) {
    //To change body of implemented methods use File | Settings | File Templates.
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
    XSourcePosition sourcePosition = myBreakpoint.getSourcePosition();
    if (sourcePosition != null) {
      panel.navigateInPreviewEditor(sourcePosition.getFile(), new LogicalPosition(sourcePosition.getLine(), sourcePosition.getOffset()));
    }

    Project project = ((XBreakpointBase)myBreakpoint).getProject();

    XBreakpointPropertiesPanel<XBreakpoint<?>> propertiesPanel =
      new XBreakpointPropertiesPanel<XBreakpoint<?>>(project, ((XBreakpointBase)myBreakpoint).getBreakpointManager(), myBreakpoint);

    panel.setDetailPanel(propertiesPanel.getMainPanel());
  }

  @Override
  public boolean allowedToRemove() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }
}
