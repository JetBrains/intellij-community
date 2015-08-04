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

/*
 * Class ValueLookupManager
 * @author Jeka
 */
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ValueLookupManager extends EditorMouseAdapter implements EditorMouseMotionListener {
  /**
   * @see com.intellij.xdebugger.XDebuggerUtil#disableValueLookup(com.intellij.openapi.editor.Editor)
   */
  public static final Key<Boolean> DISABLE_VALUE_LOOKUP = Key.create("DISABLE_VALUE_LOOKUP");

  private final Project myProject;
  private final Alarm myAlarm;
  private AbstractValueHint myRequest = null;
  private final DebuggerSupport[] mySupports;
  private boolean myListening;

  public ValueLookupManager(Project project) {
    myProject = project;
    mySupports = DebuggerSupport.getDebuggerSupports();
    myAlarm = new Alarm(project);
  }

  public void startListening() {
    if (!myListening) {
      myListening = true;
      EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(this, myProject);
      EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(this, myProject);
    }
  }

  @Override
  public void mouseDragged(EditorMouseEvent e) {
  }

  @Override
  public void mouseExited(EditorMouseEvent e) {
    myAlarm.cancelAllRequests();
  }

  @Override
  public void mouseMoved(EditorMouseEvent e) {
    if (e.isConsumed()) {
      return;
    }

    Editor editor = e.getEditor();
    if (editor.getProject() != null && editor.getProject() != myProject) {
      return;
    }

    ValueHintType type = AbstractValueHint.getHintType(e);
    if (e.getArea() != EditorMouseEventArea.EDITING_AREA ||
        DISABLE_VALUE_LOOKUP.get(editor) == Boolean.TRUE ||
        type == null) {
      myAlarm.cancelAllRequests();
      return;
    }

    Point point = e.getMouseEvent().getPoint();
    if (myRequest != null && !myRequest.isKeepHint(editor, point)) {
      hideHint();
    }

    for (DebuggerSupport support : mySupports) {
      QuickEvaluateHandler handler = support.getQuickEvaluateHandler();
      if (handler.isEnabled(myProject)) {
        requestHint(handler, editor, point, type);
        break;
      }
    }
  }

  private void requestHint(final QuickEvaluateHandler handler, final Editor editor, final Point point, @NotNull final ValueHintType type) {
    final Rectangle area = editor.getScrollingModel().getVisibleArea();
    myAlarm.cancelAllRequests();
    if (type == ValueHintType.MOUSE_OVER_HINT) {
      if (Registry.is("debugger.valueTooltipAutoShow")) {
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (area.equals(editor.getScrollingModel().getVisibleArea())) {
              showHint(handler, editor, point, type);
            }
          }
        }, getDelay(handler));
      }
    }
    else {
      showHint(handler, editor, point, type);
    }
  }

  private int getDelay(QuickEvaluateHandler handler) {
    int delay = handler.getValueLookupDelay(myProject);
    if (myRequest != null && !myRequest.isHintHidden()) {
      delay = Math.max(100, delay); // if hint is showing, delay should not be too small, see IDEA-141464
    }
    return delay;
  }

  public void hideHint() {
    if (myRequest != null) {
      myRequest.hideHint();
      myRequest = null;
    }
  }

  public void showHint(@NotNull QuickEvaluateHandler handler, @NotNull Editor editor, @NotNull Point point, @NotNull ValueHintType type) {
    myAlarm.cancelAllRequests();
    if (editor.isDisposed() || !handler.canShowHint(myProject)) {
      return;
    }

    final AbstractValueHint request = handler.createValueHint(myProject, editor, point, type);
    if (request != null) {
      if (myRequest != null && myRequest.equals(request)) {
        return;
      }

      if (!request.canShowHint()) {
        return;
      }
      if (myRequest != null && myRequest.isInsideHint(editor, point)) {
        return;
      }

      hideHint();

      myRequest = request;
      myRequest.invokeHint(new Runnable() {
        @Override
        public void run() {
          if (myRequest != null && myRequest == request) {
            myRequest = null;
          }
        }
      });
    }
  }

  public static ValueLookupManager getInstance(Project project) {
    return ServiceManager.getService(project, ValueLookupManager.class);
  }
}
