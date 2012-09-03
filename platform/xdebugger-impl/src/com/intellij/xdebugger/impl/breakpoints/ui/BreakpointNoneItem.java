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


import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XDebuggerBundle;

import javax.swing.*;

public class BreakpointNoneItem extends BreakpointItem {
  @Override
  public Object getBreakpoint() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void setEnabled(boolean state) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isDefaultBreakpoint() {
    return true;
  }

  @Override
  protected void setupGenericRenderer(SimpleColoredComponent renderer, boolean plainView) {
    renderer.clear();
    renderer.append(getDisplayText());
  }

  @Override
  public Icon getIcon() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getDisplayText() {
    return XDebuggerBundle.message("xbreakpoint.master.breakpoint.none");
  }

  @Override
  public boolean navigate() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String speedSearchText() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String footerText() {
    return "";
  }

  @Override
  protected void doUpdateDetailView(DetailView panel, boolean editorOnly) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean allowedToRemove() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void removed(Project project) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public int compareTo(BreakpointItem breakpointItem) {
    return 1;
  }
}
