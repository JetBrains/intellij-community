// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common;

/*
 * Class ValueLookupManager
 * @author Jeka
 */

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.settings.DataViewsConfigurableUi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController.DISABLE_VALUE_LOOKUP;

@ApiStatus.Internal
public class ValueLookupManager implements EditorMouseMotionListener, EditorMouseListener {
  private final Project myProject;
  private final Alarm myAlarm;
  private HintRequest myHintRequest = null;
  private AbstractValueHint myCurrentHint = null;
  private final @NotNull QuickEvaluateHandler myXQuickEvaluateHandler = new XQuickEvaluateHandler();
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
    if (myHintRequest != null) {
      myHintRequest.cancellableHint.tryCancel();
      myHintRequest = null;
    }
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    if (e.isConsumed() || !Registry.is(DataViewsConfigurableUi.DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY)) {
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

    if (type != ValueHintType.MOUSE_CLICK_HINT) { // click should always trigger a new hint
      if ((myHintRequest != null && myHintRequest.type == ValueHintType.MOUSE_CLICK_HINT) ||
          (myCurrentHint != null && myCurrentHint.getType() == ValueHintType.MOUSE_CLICK_HINT)) {
        return;
      }
    }

    Point point = e.getMouseEvent().getPoint();

    // handle platform handler first
    if (myXQuickEvaluateHandler.isEnabled(myProject)) {
      requestHint(myXQuickEvaluateHandler, editor, point, e, type);
      return;
    }
    // otherwise, handle plugin handlers
    // for remote dev: specific DebuggerSupport with remote bridge will be used
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      QuickEvaluateHandler handler = support.getQuickEvaluateHandler();
      if (handler.isEnabled(myProject)) {
        requestHint(handler, editor, point, e, type);
        return;
      }
    }

    // if no providers were triggered - hide
    hideHint();
  }

  private void requestHint(final QuickEvaluateHandler handler,
                           final Editor editor,
                           final Point point,
                           @NotNull EditorMouseEvent e,
                           @NotNull final ValueHintType type) {
    final Rectangle area = editor.getScrollingModel().getVisibleArea();
    cancelAll();
    if (type == ValueHintType.MOUSE_OVER_HINT) {
      if (Registry.is(DataViewsConfigurableUi.DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY)) {
        myAlarm.addRequest(() -> {
          if (area.equals(editor.getScrollingModel().getVisibleArea())) {
            showHint(handler, editor, point, e, type);
          }
        }, getDelay(handler));
      }
    }
    else {
      showHint(handler, editor, point, e, type);
    }
  }

  private int getDelay(QuickEvaluateHandler handler) {
    int delay = handler.getValueLookupDelay(myProject);
    if (myCurrentHint != null && !myCurrentHint.isHintHidden()) {
      delay = Math.max(100, delay); // if hint is showing, delay should not be too small, see IDEA-141464
    }
    return delay;
  }

  public void hideHint() {
    if (myCurrentHint != null) {
      if (myCurrentHint instanceof RemoteValueHint) {
        // if ValueLookupManager requests hideHint, then it should be closed on backend as well
        // otherwise the hint may continue living on backend itself
        ((RemoteValueHint)myCurrentHint).hideHint(true);
      }
      else {
        myCurrentHint.hideHint();
      }
      myCurrentHint = null;
    }
  }

  public void showHint(@NotNull QuickEvaluateHandler handler,
                       @NotNull Editor editor,
                       @NotNull Point point,
                       @Nullable EditorMouseEvent e,
                       @NotNull ValueHintType type) {
    PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(() -> doShowHint(handler, editor, point, e, type));
  }

  private void doShowHint(@NotNull QuickEvaluateHandler handler,
                          @NotNull Editor editor,
                          @NotNull Point point,
                          @Nullable EditorMouseEvent event,
                          @NotNull ValueHintType type) {
    cancelAll();
    if (editor.isDisposed() || !handler.canShowHint(myProject)) {
      return;
    }
    if (myCurrentHint != null && myCurrentHint.isInsideHint(editor, point)) {
      return;
    }
    if (event != null && !event.isOverText()) { // do not trigger if there's no text below
      return;
    }

    try {
      QuickEvaluateHandler.CancellableHint cancellableHint = handler.createValueHintAsync(myProject, editor, point, type);
      HintRequest hintRequest = new HintRequest(cancellableHint, type);
      myHintRequest = hintRequest;
      cancellableHint.hintPromise().onProcessed(hint -> {
        if (myHintRequest == hintRequest) { // clear request if it has not changed
          myHintRequest = null;
        }
        if (hint == null) {
          UIUtil.invokeLaterIfNeeded(() -> {
            hideHint();
            if (event != null) {
              EditorMouseHoverPopupManager.getInstance().showInfoTooltip(event);
            }
          });
          return;
        }
        if (myCurrentHint != null && myCurrentHint.equals(hint)) {
          return;
        }
        if (event != null) {
          hint.setEditorMouseEvent(event);
        }
        UIUtil.invokeLaterIfNeeded(() -> {
          hideHint();

          myCurrentHint = hint;
          myCurrentHint.invokeHint(() -> {
            if (myCurrentHint == hint) {
              myCurrentHint = null;
            }
          });
        });
      });
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  public static ValueLookupManager getInstance(Project project) {
    return project.getService(ValueLookupManager.class);
  }

  private record HintRequest(@NotNull QuickEvaluateHandler.CancellableHint cancellableHint, @NotNull ValueHintType type) {
  }
}
