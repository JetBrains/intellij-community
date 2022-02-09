// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public final class PotemkinOverlayProgress extends AbstractProgressIndicatorBase
  implements StandardProgressIndicator, ProgressIndicatorWithDelayedPresentation, PingProgress, Disposable {

  private static final MouseShortcut SHORTCUT = new MouseShortcut(
    1, (SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK) |
       InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 1);

  private final Component myComponent;
  private final PotemkinProgress.EventStealer myEventStealer;
  private final long myCreatedAt = System.currentTimeMillis();
  private int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS * 10;
  private long myLastUiUpdate = myCreatedAt;
  private long myLastInteraction;
  private boolean myShowing;

  public PotemkinOverlayProgress(@Nullable Component component) {
    EDT.assertIsEdt();
    myComponent = component;
    myEventStealer = PotemkinProgress.startStealingInputEvents(this::dispatchInputEvent, this);
  }

  @Override
  public void setDelayInMillis(int delayInMillis) {
    myDelayInMillis = delayInMillis;
  }

  @Override
  public void stop() {
    try {
      super.stop();
      Disposer.dispose(this);
    }
    finally {
      myEventStealer.dispatchEvents(0);
    }
  }

  @Override
  public void dispose() {
    if (myShowing) {
      JRootPane rootPane = UIUtil.getParentOfType(JRootPane.class, myComponent);
      if (rootPane != null) rootPane.repaint();
    }
  }

  @Override
  public void interact() {
    if (!EDT.isCurrentThreadEdt()) return;
    long now = System.currentTimeMillis();
    if (now == myLastInteraction) return;
    myLastInteraction = now;
    myEventStealer.dispatchEvents(0);
    if (!myShowing && now - myLastUiUpdate > myDelayInMillis) {
      myShowing = true;
    }
    if (myShowing) {
      updateUI(now);
    }
  }

  private void dispatchInputEvent(@NotNull InputEvent event) {
    if (!(event instanceof MouseEvent)) return;
    boolean isCancellation = event.getID() == MouseEvent.MOUSE_PRESSED &&
                             (event.getModifiersEx() & SHORTCUT.getModifiers()) == SHORTCUT.getModifiers() &&
                             ((MouseEvent)event).getButton() == SHORTCUT.getButton();
    if (isCancellation) {
      cancel();
    }
  }

  private void updateUI(long now) {
    if (now - myLastUiUpdate <= ProgressDialog.UPDATE_INTERVAL) {
      return;
    }
    myLastUiUpdate = now;
    paintProgress();
  }

  private void paintProgress() {
    JRootPane rootPane = UIUtil.getParentOfType(JRootPane.class, myComponent);
    IdeGlassPane glassPane = rootPane == null ? null : ObjectUtils.tryCast(rootPane.getGlassPane(), IdeGlassPane.class);
    if (glassPane == null) return;
    long roundedDuration = (System.currentTimeMillis() - myCreatedAt) / 1000 * 1000;
    //noinspection HardCodedStringLiteral
    String text = "Use " + KeymapUtil.getMouseShortcutText(SHORTCUT) + " to cancel (" +
                  NlsMessages.formatDurationApproximateNarrow(roundedDuration) + ")";
    Graphics graphics = rootPane.getGraphics();
    Rectangle viewR = rootPane.getBounds(), iconR = new Rectangle(), textR = new Rectangle();
    FontMetrics fm = graphics.getFontMetrics();
    SwingUtilities.layoutCompoundLabel(fm, text, null, 0, 0, 0, 0, viewR, iconR, textR, 0);
    graphics.translate(textR.x, textR.y);
    graphics.setColor(JBColor.GRAY);
    int border = 10;
    graphics.fillRoundRect(-border, -border, textR.width + 2 * border, textR.height + 2 * border, border, border);
    graphics.setColor(JBColor.WHITE);
    graphics.drawString(text, 0, fm.getAscent());
    graphics.dispose();
  }
}