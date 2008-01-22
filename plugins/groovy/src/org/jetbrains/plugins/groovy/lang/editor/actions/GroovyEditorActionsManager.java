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

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.plugins.groovy.lang.editor.actions.GroovyEnterHandler;
import org.jetbrains.plugins.grails.lang.gsp.editor.actions.GspTypedHandler;

/**
 * @author ilyas
 */
public class GroovyEditorActionsManager {

  public static void registerGroovyEditorActions() {
    EditorActionManager manager = EditorActionManager.getInstance();
    registerEnterActionHandler(manager);
    registerTypedActionHandler(manager);
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
