// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.TaskBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.configurationStore.XmlSerializer.deserialize;
import static com.intellij.configurationStore.XmlSerializer.serialize;

/**
 * @author Dmitry Avdeev
 */
final class XDebuggerBreakpointsContextProvider extends WorkingContextProvider {
  @NotNull
  @Override
  public String getId() {
    return "xDebugger";
  }

  @NotNull
  @Override
  public String getDescription() {
    return TaskBundle.message("xdebugger.breakpoints");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) throws WriteExternalException {
    BreakpointManagerState state = new BreakpointManagerState();
    getBreakpointManager(XDebuggerManager.getInstance(project)).saveState(state);
    Element serialize = serialize(state);
    if (serialize != null) {
      toElement.addContent(serialize.removeContent());
    }
  }

  @NotNull
  private static XBreakpointManagerImpl getBreakpointManager(XDebuggerManager instance) {
    return (XBreakpointManagerImpl)instance.getBreakpointManager();
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) throws InvalidDataException {
    getBreakpointManager(XDebuggerManager.getInstance(project)).loadState(deserialize(fromElement, BreakpointManagerState.class));
  }

  @Override
  public void clearContext(@NotNull Project project) {
    XBreakpointManagerImpl breakpointManager = getBreakpointManager(XDebuggerManager.getInstance(project));
    XBreakpointBase<?,?,?>[] breakpoints = breakpointManager.getAllBreakpoints();
    for (final XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      ApplicationManager.getApplication().runWriteAction(() -> breakpointManager.removeBreakpoint(breakpoint));
    }
  }
}
