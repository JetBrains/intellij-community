/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * @author nik
 */
public class QuickEvaluateAction extends XDebuggerActionBase {
  public QuickEvaluateAction() {
    super(true);
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return new QuickEvaluateHandlerWrapper(debuggerSupport.getQuickEvaluateHandler());
  }

  private static class QuickEvaluateHandlerWrapper extends DebuggerActionHandler {
    private final QuickEvaluateHandler myHandler;

    public QuickEvaluateHandlerWrapper(final QuickEvaluateHandler handler) {
      myHandler = handler;
    }

    public void perform(@NotNull final Project project, final AnActionEvent event) {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        ValueLookupManager.getInstance(project).
          showHint(myHandler, editor, editor.logicalPositionToXY(logicalPosition), ValueHintType.MOUSE_CLICK_HINT);
      }
    }

    public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
      if (!myHandler.isEnabled(project)) return false;

      Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor == null) return false;

      InputEvent inputEvent = event.getInputEvent();
      if (inputEvent instanceof MouseEvent && inputEvent.isAltDown()) {
        MouseEvent mouseEvent = (MouseEvent)inputEvent;
        Component component = SwingUtilities.getDeepestComponentAt(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
        if (SwingUtilities.isDescendingFrom(component, editor.getComponent())) {
          MouseEvent convertedEvent = SwingUtilities.convertMouseEvent(mouseEvent.getComponent(), mouseEvent, component);
          EditorMouseEventArea area = editor.getMouseEventArea(convertedEvent);
          if (area != EditorMouseEventArea.EDITING_AREA) {
            return false;
          }
        }
      }

      return true;
    }
  }
}
