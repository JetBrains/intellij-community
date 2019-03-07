// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.WatchesManagerState;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.configurationStore.XmlSerializer.deserialize;
import static com.intellij.configurationStore.XmlSerializer.serialize;

/**
 * @author Dmitry Avdeev
 */
public class XDebuggerWatchesProvider extends WorkingContextProvider {
  private final XDebuggerWatchesManager myWatchesManager;

  public XDebuggerWatchesProvider(XDebuggerManager xDebuggerManager) {
    myWatchesManager = ((XDebuggerManagerImpl)xDebuggerManager).getWatchesManager();
  }

  @NotNull
  @Override
  public String getId() {
    return "watches";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Debugger watches";
  }

  @Override
  public void saveContext(@NotNull Element toElement) throws WriteExternalException {
    WatchesManagerState state = new WatchesManagerState();
    myWatchesManager.saveState(state);
    Element serialize = serialize(state);
    if (serialize != null) {
      toElement.addContent(serialize.removeContent());
    }
  }

  @Override
  public void loadContext(@NotNull Element fromElement) throws InvalidDataException {
    WatchesManagerState state = deserialize(fromElement, WatchesManagerState.class);
    myWatchesManager.loadState(state);

  }

  @Override
  public void clearContext() {
    myWatchesManager.clearContext();
  }
}
