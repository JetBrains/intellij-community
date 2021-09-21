// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import org.jetbrains.annotations.NotNull;

/**
 * Help interface with methods that can recognize a type of {@link XDebugProcess}.
 */
public interface DebuggerToolbarAction {

  Key<Class<?>> PROCESS_TYPE_KEY = Key.create("Debugger Process Type");

  default <T extends XDebugProcess> void update(@NotNull AnActionEvent e, @NotNull Class<T> processType) {
    e.getPresentation().setVisible(checkDebugProcessType(e, processType));
    e.getPresentation().setEnabled(e.getData(XDebugSession.DATA_KEY) != null);
  }

  /**
   * Retrieves debug process from context and checks its type.
   *
   * Also, remembers the process type if the session was stopped,
   * therefore the action shouldn't disappear after session is stopped.
   *
   * @param e action event with debugger context
   * @param processType type to be checked
   * @return true if debug session has required process type
   */
  default <T extends XDebugProcess> boolean checkDebugProcessType(@NotNull AnActionEvent e, @NotNull Class<T> processType) {
    var data = e.getData(XDebugSessionData.DATA_KEY);
    if (data == null) {
      return false;
    }
    var memorizedType = data.getUserData(PROCESS_TYPE_KEY);
    if (memorizedType == null) {
      var session = e.getData(XDebugSession.DATA_KEY);
      if (session != null) {
        data.putUserData(PROCESS_TYPE_KEY, memorizedType = session.getDebugProcess().getClass());
      }
    }
    return memorizedType != null && processType.isAssignableFrom(memorizedType);
  }

}
