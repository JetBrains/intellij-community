// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class ValueLookupManager
 * @author Jeka
 */
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.awt.*;

public class ValueLookupManager extends EditorMouseAdapter implements EditorMouseMotionListener {
  /**
   * @see XDebuggerUtil#disableValueLookup(Editor)
   */
  public static final Key<Boolean> DISABLE_VALUE_LOOKUP = Key.create("DISABLE_VALUE_LOOKUP");

  private final Project myProject;
  private final Alarm myAlarm;
  private AbstractValueHint myRequest = null;
  private final DebuggerSupport[] mySupports;
  private boolean myListening;

  public ValueLookupManager(@NotNull Project project) {
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

    if (type == ValueHintType.MOUSE_OVER_HINT && !ApplicationManager.getApplication().isActive()) {
      return;
    }

    Point point = e.getMouseEvent().getPoint();
    if (myRequest != null) {
      if (myRequest.getType() == ValueHintType.MOUSE_CLICK_HINT) {
        return;
      }
      else if (!myRequest.isKeepHint(editor, point)) {
        hideHint();
      }
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
        myAlarm.addRequest(() -> {
          if (area.equals(editor.getScrollingModel().getVisibleArea())) {
            showHint(handler, editor, point, type);
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
    PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(() -> doShowHint(handler, editor, point, type));
  }

  private void doShowHint(@NotNull QuickEvaluateHandler handler,
                          @NotNull Editor editor,
                          @NotNull Point point,
                          @NotNull ValueHintType type) {
    myAlarm.cancelAllRequests();
    if (editor.isDisposed() || !handler.canShowHint(myProject)) {
      return;
    }
    if (myRequest != null && myRequest.isInsideHint(editor, point)) {
      return;
    }
    Promise<AbstractValueHint> hintPromise;
    try {
      hintPromise = handler.createValueHintAsync(myProject, editor, point, type);
    }
    catch (IndexNotReadyException e) {
      return;
    }
    hintPromise.onSuccess(hint -> {
      if (hint == null)
        return;
      if (myRequest != null && myRequest.equals(hint)) {
        return;
      }
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!hint.canShowHint()) {
          return;
        }

        hideHint();

        myRequest = hint;
        myRequest.invokeHint(() -> {
          if (myRequest != null && myRequest == hint) {
            myRequest = null;
          }
        });
     });
    });
  }

  public static ValueLookupManager getInstance(Project project) {
    return ServiceManager.getService(project, ValueLookupManager.class);
  }
}
