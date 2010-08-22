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
package com.intellij.xdebugger.impl.breakpoints.ui.actions;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelAction;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointsPanel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
*/
public class GoToBreakpointAction<B extends XBreakpoint<?>> extends XBreakpointPanelAction<B> {
  private final boolean myCloseDialog;

  public GoToBreakpointAction(final @NotNull XBreakpointsPanel<B> panel, final String name, boolean closeDialog) {
    super(panel, name);
    myCloseDialog = closeDialog;
  }

  public boolean isEnabled(@NotNull final Collection<? extends B> breakpoints) {
    if (breakpoints.size() != 1) {
      return false;
    }
    B b = breakpoints.iterator().next();
    Navigatable navigatable = b.getNavigatable();
    return navigatable != null && navigatable.canNavigateToSource();
  }

  public void perform(@NotNull final Collection<? extends B> breakpoints) {
    B b = breakpoints.iterator().next();
    Navigatable navigatable = b.getNavigatable();
    if (navigatable != null) {
      navigatable.navigate(true);
    }
    if (myCloseDialog) {
      myBreakpointsPanel.getParentDialog().close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
