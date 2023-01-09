// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class ValueLookupManager
 * @author Jeka
 */
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
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

import java.awt.*;

public class ValueLookupManager implements EditorMouseMotionListener, EditorMouseListener {
  /**
   * @see XDebuggerUtil#disableValueLookup(Editor)
   */
  public static final Key<Boolean> DISABLE_VALUE_LOOKUP = Key.create("DISABLE_VALUE_LOOKUP");

  private final Project myProject;
  private final Alarm myAlarm;
  private QuickEvaluateHandler.CancellableHint myCancellableHint = null;
  private AbstractValueHint myRequest = null;
  private boolean myListening;

  public ValueLookupManager(@NotNull Project project) {
    myProject = project;
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
  public void mouseExited(@NotNull EditorMouseEvent e) {
    cancelAll();
  }

  private void cancelAll() {
    myAlarm.cancelAllRequests();
    if (myCancellableHint != null) {
      myCancellableHint.tryCancel();
      myCancellableHint = null;
    }
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
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
      cancelAll();
      return;
    }

    if (type == ValueHintType.MOUSE_OVER_HINT && !ApplicationManager.getApplication().isActive()) {
      hideHint();
      return;
    }

    Point point = e.getMouseEvent().getPoint();
    if (myRequest != null && myRequest.getType() == ValueHintType.MOUSE_CLICK_HINT) {
      return;
    }

    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      QuickEvaluateHandler handler = support.getQuickEvaluateHandler();
      if (handler.isEnabled(myProject)) {
        requestHint(handler, editor, point, type);
        return;
      }
    }

    // if no providers were triggered - hide
    hideHint();
  }

  private void requestHint(final QuickEvaluateHandler handler, final Editor editor, final Point point, @NotNull final ValueHintType type) {
    final Rectangle area = editor.getScrollingModel().getVisibleArea();
    cancelAll();
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
    cancelAll();
    if (editor.isDisposed() || !handler.canShowHint(myProject)) {
      return;
    }
    if (myRequest != null && myRequest.isInsideHint(editor, point)) {
      return;
    }

    try {
      myCancellableHint = handler.createValueHintAsync(myProject, editor, point, type);
    }
    catch (IndexNotReadyException e) {
      return;
    }
    myCancellableHint.hintPromise().onSuccess(hint -> {
      if (hint == null) {
        UIUtil.invokeLaterIfNeeded(this::hideHint);
        return;
      }
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
    return project.getService(ValueLookupManager.class);
  }
}
