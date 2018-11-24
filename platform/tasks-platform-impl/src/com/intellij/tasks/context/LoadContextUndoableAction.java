/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.context;

import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.tasks.Task;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 */
public abstract class LoadContextUndoableAction extends GlobalUndoableAction {

  protected final WorkingContextManager myManager;
  private final boolean myClear;
  @NonNls private static final String SNAPSHOT = "snapshot";

  public static LoadContextUndoableAction createAction(WorkingContextManager manager, boolean clear, final String contextName) {
    return new LoadContextUndoableAction(manager, clear) {
      @Override
      protected void doLoad() {
        myManager.loadContext(contextName);
      }
    };
  }

  public static LoadContextUndoableAction createAction(WorkingContextManager manager, boolean clear, final Task task) {
    return new LoadContextUndoableAction(manager, clear) {
      @Override
      protected void doLoad() {
        myManager.restoreContext(task);
      }
    };
  }

  private LoadContextUndoableAction(WorkingContextManager manager, boolean clear) {
    myManager = manager;
    myClear = clear;
  }

  public void undo() throws UnexpectedUndoException {
    myManager.clearContext();
    myManager.loadContext(SNAPSHOT);
  }

  public void redo() {
    myManager.saveContext(SNAPSHOT, null);
    if (myClear) {
      myManager.clearContext();
    }
    doLoad();
  }

  protected abstract void doLoad();
}
