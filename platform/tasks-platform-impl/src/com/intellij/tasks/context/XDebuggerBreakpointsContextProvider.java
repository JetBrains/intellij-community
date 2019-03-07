// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
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
public class XDebuggerBreakpointsContextProvider extends WorkingContextProvider {
  private final XBreakpointManagerImpl myBreakpointManager;

  public XDebuggerBreakpointsContextProvider(XDebuggerManager xDebuggerManager) {
    myBreakpointManager = (XBreakpointManagerImpl)xDebuggerManager.getBreakpointManager();
  }

  @NotNull
  @Override
  public String getId() {
    return "xDebugger";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "XDebugger breakpoints";
  }

  @Override
  public void saveContext(@NotNull Element toElement) throws WriteExternalException {
    BreakpointManagerState state = new BreakpointManagerState();
    myBreakpointManager.saveState(state);
    Element serialize = serialize(state);
    if (serialize != null) {
      toElement.addContent(serialize.removeContent());
    }
  }

  @Override
  public void loadContext(@NotNull Element fromElement) throws InvalidDataException {
    myBreakpointManager.loadState(deserialize(fromElement, BreakpointManagerState.class));
  }

  @Override
  public void clearContext() {
    XBreakpointBase<?,?,?>[] breakpoints = myBreakpointManager.getAllBreakpoints();
    for (final XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      ApplicationManager.getApplication().runWriteAction(() -> myBreakpointManager.removeBreakpoint(breakpoint));
    }
  }
}
