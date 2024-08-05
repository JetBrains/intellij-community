// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  @Override
  public void undo() throws UnexpectedUndoException {
    myManager.clearContext();
    myManager.loadContext(SNAPSHOT);
  }

  @Override
  public void redo() {
    myManager.saveContext(SNAPSHOT, null);
    if (myClear) {
      myManager.clearContext();
    }
    doLoad();
  }

  protected abstract void doLoad();
}
