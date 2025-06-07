// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.TaskBundle;
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
final class XDebuggerWatchesProvider extends WorkingContextProvider {
  @Override
  public @NotNull String getId() {
    return "watches";
  }

  @Override
  public @NotNull String getDescription() {
    return TaskBundle.message("debugger.watches");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) throws WriteExternalException {
    WatchesManagerState state = new WatchesManagerState();
    getWatchManager(project).saveState(state);
    Element serialize = serialize(state);
    if (serialize != null) {
      toElement.addContent(serialize.removeContent());
    }
  }

  private static @NotNull XDebuggerWatchesManager getWatchManager(@NotNull Project project) {
    return ((XDebuggerManagerImpl)XDebuggerManager.getInstance(project)).getWatchesManager();
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) throws InvalidDataException {
    WatchesManagerState state = deserialize(fromElement, WatchesManagerState.class);
    getWatchManager(project).loadState(state);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    getWatchManager(project).clearContext();
  }
}
