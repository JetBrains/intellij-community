/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class XBreakpointPanelAction<B extends XBreakpoint<?>> implements ActionListener {
  protected final XBreakpointsPanel<B> myBreakpointsPanel;
  private final String myName;

  protected XBreakpointPanelAction(final @NotNull XBreakpointsPanel<B> breakpointsPanel, @NotNull String name) {
    myBreakpointsPanel = breakpointsPanel;
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public abstract boolean isEnabled(@NotNull Collection<? extends B> breakpoints);

  public abstract void perform(@NotNull Collection<? extends B> breakpoints);

  @Override
  public void actionPerformed(ActionEvent e) {
    List<B> list = myBreakpointsPanel.getTree().getSelectedBreakpoints();
    perform(list);
  }
}
