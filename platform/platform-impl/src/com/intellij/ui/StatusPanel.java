// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class StatusPanel extends JBPanel {
  private final ReentrantLock myLock = new ReentrantLock();
  @Nullable private @Nls String myError;
  /**
   * Guarded by {@link #myLock}.
   */
  @Nullable private Action myCurrentAction;

  public StatusPanel() {
    super(new BorderLayout());
    setVisible(false);
  }

  /**
   * Cancels current action and clears UI.
   * <p/>
   * <b>NB!</b> Must be called within EDT.
   */
  public void resetState() {
    myLock.lock();
    try {
      cancelCurrentAction();
      clearUI();
    }
    finally {
      myLock.unlock();
    }
  }

  /**
   * Cancels current action and begins waiting for result of the new one.
   *
   * @param message informational message (not shown)
   * @return new action with methods to be invoked to notify about its result
   */
  @NotNull
  public Action progress(@NotNull @Nls String message) {
    return progress(message, true);
  }

  /**
   * Cancels current action and begins waiting for result of the new one.
   *
   * @param message              informational message (not shown)
   * @param addProgressIconBelow put animated progress below all components. Can be omitted if form provides its own progress.
   * @return new action with methods to be invoked to notify about its result
   */
  @NotNull
  public Action progress(@NotNull @Nls String message, boolean addProgressIconBelow) {
    myLock.lock();
    try {
      cancelCurrentAction();

      Action action = new Action();
      myCurrentAction = action;
      invokeLater(() -> {
        clearUI();

        if (addProgressIconBelow) {
          AsyncProcessIcon asyncProcessIcon = new AsyncProcessIcon(message);
          add(asyncProcessIcon, BorderLayout.CENTER);
          setVisible(true);
          asyncProcessIcon.resume();
        }

        refreshUI();
      });
      return action;
    }
    finally {
      myLock.unlock();
    }
  }

  /**
   * <b>NB!</b> Must be called while holding {@link #myLock}.
   */
  private void cancelCurrentAction() {
    if (myCurrentAction != null) {
      myCurrentAction.cancel();
      myCurrentAction = null;
    }
  }

  /**
   * <b>NB!</b> Must be called within EDT.
   */
  private void clearUI() {
    myError = null;

    removeAll();

    setVisible(false);

    refreshUI();
  }

  /**
   * <b>NB!</b> Must be called within EDT.
   */
  private void refreshUI() {
    revalidate();
    repaint();
  }

  /**
   * <b>NB!</b> Must be called within EDT.
   */
  private void showMessage(@NlsContexts.DialogMessage @NotNull String message, @NotNull JBColor color, @Nullable Icon statusIcon) {
    removeAll();

    setVisible(true);

    JBLabel label = new JBLabel(message);
    label
      .setText(HtmlChunk.tag("font").attr("color", "#" + ColorUtil.toHex(color))
               .child(HtmlChunk.tag("left").addRaw(message))
               .wrapWith("html")
               .toString());
    label.setIcon(statusIcon);
    label.setBorder(new EmptyBorder(4, 10, 0, 2));

    add(label, BorderLayout.CENTER);

    refreshUI();
  }

  private void invokeLater(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(this));
  }

  @Nullable
  public @NlsContexts.DialogMessage String getError() {
    return myError;
  }

  public class Action {
    /**
     * Guarded by {@link #myLock}.
     */
    private volatile boolean myCancelled = false;
    private final AtomicBoolean myCompleted = new AtomicBoolean(false);

    private boolean checkIsInProgressAndComplete() {
      // optional early check of action cancellation
      return myCompleted.compareAndSet(false, true) && !myCancelled;
    }

    /**
     * If action is cancelled it cannot become active anymore.
     * <p/>
     * <b>NB!</b> Must be called while holding {@link #myLock}.
     */
    private void cancel() {
      myCancelled = true;
    }

    public void done() {
      if (checkIsInProgressAndComplete()) {
        invokeLater(() -> {
          myLock.lock();
          try {
            if (!myCancelled) {
              clearUI();
            }
          }
          finally {
            myLock.unlock();
          }
        });
      }
    }

    public void doneWithResult(@NotNull @NlsContexts.DialogMessage String message) {
      showMessageOnce(message, false);
    }

    public void failed(@Nullable @NlsContexts.DialogMessage String message) {
      showMessageOnce(message, true);
    }

    private void showMessageOnce(@Nullable @NlsContexts.DialogMessage String message, boolean isError) {
      if (checkIsInProgressAndComplete()) {
        invokeLater(() -> {
          myLock.lock();
          try {
            if (!myCancelled) {
              JBColor color = isError ? JBColor.RED : JBColor.DARK_GRAY;
              Icon statusIcon = isError ? AllIcons.Actions.Lightning : AllIcons.General.InspectionsOK;
              myError = isError ? message : null;
              showMessage(StringUtil.notNullize(message), color, statusIcon);
            }
          }
          finally {
            myLock.unlock();
          }
        });
      }
    }
  }
}
