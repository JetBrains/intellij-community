/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown.GroovyMoveStatementHandler;

/**
 * @author ilyas
 */
public class GroovyEditorActionsManager {
  public static final String MOVE_STATEMENT_UP_ACTION = "MoveStatementUp";
  public static final String MOVE_STATEMENT_DOWN_ACTION = "MoveStatementDown";

  public static void registerGroovyEditorActions() {
    EditorActionManager manager = EditorActionManager.getInstance();
    registerEnterActionHandler(manager);
    registerTypedActionHandler(manager);
    registerMoveActionHandlers(manager);
  }

  private static void registerMoveActionHandlers(EditorActionManager manager) {
    EditorActionHandler downHandler = new GroovyMoveStatementHandler(manager.getActionHandler(MOVE_STATEMENT_DOWN_ACTION), true);
    manager.setActionHandler(MOVE_STATEMENT_DOWN_ACTION, downHandler);
    assert (downHandler == manager.getActionHandler(MOVE_STATEMENT_DOWN_ACTION));

    EditorActionHandler upHandler = new GroovyMoveStatementHandler(manager.getActionHandler(MOVE_STATEMENT_UP_ACTION), false);
    manager.setActionHandler(MOVE_STATEMENT_UP_ACTION, upHandler);
    assert (upHandler == manager.getActionHandler(MOVE_STATEMENT_UP_ACTION));
  }

  private static EditorActionHandler registerEnterActionHandler(EditorActionManager manager) {
    EditorActionHandler handler = new GroovyEnterHandler(manager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER));
    manager.setActionHandler(IdeActions.ACTION_EDITOR_ENTER, handler);
    assert (handler == manager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER));
    return handler;
  }

  private static TypedActionHandler registerTypedActionHandler(EditorActionManager manager) {
    TypedAction originalTypedAction = manager.getTypedAction();
    TypedActionHandler handler = new GroovyTypedHandler(originalTypedAction.getHandler());
    originalTypedAction.setupHandler(handler);
    return handler;
  }

}
