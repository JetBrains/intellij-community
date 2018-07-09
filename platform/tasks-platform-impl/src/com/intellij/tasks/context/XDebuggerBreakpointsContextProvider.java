/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  public void saveContext(Element toElement) throws WriteExternalException {
    BreakpointManagerState state = new BreakpointManagerState();
    myBreakpointManager.saveState(state);
    Element serialize = serialize(state);
    if (serialize != null) {
      toElement.addContent(serialize.removeContent());
    }
  }

  @Override
  public void loadContext(Element fromElement) throws InvalidDataException {
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
