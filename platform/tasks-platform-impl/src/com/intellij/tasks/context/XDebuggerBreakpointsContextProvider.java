// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.SlowOperations;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.configurationStore.XmlSerializer.deserialize;
import static com.intellij.configurationStore.XmlSerializer.serialize;

/**
 * @author Dmitry Avdeev
 */
final class XDebuggerBreakpointsContextProvider extends WorkingContextProvider {
  @Override
  public @NotNull String getId() {
    return "xDebugger";
  }

  @Override
  public @NotNull String getDescription() {
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

  private static @NotNull XBreakpointManagerImpl getBreakpointManager(XDebuggerManager instance) {
    return (XBreakpointManagerImpl)instance.getBreakpointManager();
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) throws InvalidDataException {
    XDebuggerManager debuggerManager = XDebuggerManager.getInstance(project);
    XBreakpointManagerImpl breakpointManager = getBreakpointManager(debuggerManager);
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-318215, EA-836694")) {
      breakpointManager.loadState(deserialize(fromElement, BreakpointManagerState.class));
    }
  }

  @Override
  public void clearContext(@NotNull Project project) {
    XDebuggerUtilImpl.removeAllBreakpoints(project);
  }
}
