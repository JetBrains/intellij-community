// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.ide.actions.DumpThreadsAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.JBColor;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

@ApiStatus.Internal
public final class PotemkinOverlayProgress extends AbstractProgressIndicatorBase
  implements StandardProgressIndicator, ProgressIndicatorWithDelayedPresentation, PingProgress, Disposable {

  private static final KeyboardShortcut CANCEL_SHORTCUT = new KeyboardShortcut(
    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), null);
  private static final KeyboardShortcut DUMP_SHORTCUT = new KeyboardShortcut(
    KeyStroke.getKeyStroke(KeyEvent.VK_D, (SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK)), null);

  private final Component myComponent;
  private final EventStealer myEventStealer;
  private final long myCreatedAt = System.nanoTime();
  private long myDelayInMillis = ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS * 10;
  private long myLastUiUpdate = myCreatedAt;
  private long myLastInteraction;
  private boolean myShowing;
  private final boolean myCancellable;

  static {
    // preload classes
    //noinspection ResultOfMethodCallIgnored
    NlsMessages.formatDurationApproximateNarrow(0);
  }

  @Obsolete
  public PotemkinOverlayProgress(@Nullable Component component) {
    this(component, true);
  }

  @Obsolete
  public PotemkinOverlayProgress(@Nullable Component component, boolean cancellable) {
    EDT.assertIsEdt();
    myCancellable = cancellable;
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
    }
    finally {
      try {
        Disposer.dispose(this);
      }
      finally {
        myEventStealer.dispatchEvents(0);
      }
    }
  }

  @Override
  public void dispose() {
    if (myShowing) {
      JRootPane rootPane = SwingUtilities.getRootPane(myComponent);
      if (rootPane != null) rootPane.repaint();
    }
  }

  @Override
  public void interact() {
    if (!EDT.isCurrentThreadEdt()) return;
    long now = System.nanoTime();
    if (now == myLastInteraction) return;
    myLastInteraction = now;
    long millisSinceLastUpdate = TimeoutUtil.getDurationMillis(myLastUiUpdate);
    if (!myShowing && millisSinceLastUpdate > myDelayInMillis) {
      // since we are starting to show the dialog, we need to emulate modality and drop unrelated input events
      // the only events that are allowed here are the ones that related to the dialog;
      // but we know that there are no such events because the dialog is not showing yet
      drainUndispatchedInputEvents();
      myShowing = true;
    }
    if (myShowing) {
      myEventStealer.dispatchEvents(0);
    }
    if (myShowing && millisSinceLastUpdate > ProgressDialog.UPDATE_INTERVAL) {
      myLastUiUpdate = System.nanoTime();
      paintProgress();
    }
  }

  private void dispatchInputEvent(@NotNull InputEvent event) {
    Shortcut shortcut = createShortcut(event);
    if (shortcut == null) return;
    if (CANCEL_SHORTCUT.equals(shortcut)) {
      event.consume();
      cancel();
    }
    else if (DUMP_SHORTCUT.equals(shortcut)) {
      event.consume();
      Project project = ProjectUtil.getProjectForComponent(myComponent);
      DumpThreadsAction.dumpThreads(project);
    }
  }

  private static @Nullable Shortcut createShortcut(@NotNull InputEvent event) {
    if (event instanceof MouseEvent) {
      return KeymapUtil.createMouseShortcut((MouseEvent)event);
    }
    else if (event instanceof KeyEvent) {
      KeyStroke ks = KeyStrokeAdapter.getDefaultKeyStroke((KeyEvent)event);
      return ks == null ? null : new KeyboardShortcut(ks, null);
    }
    return null;
  }

  private List<InputEvent> drainUndispatchedInputEvents() {
    return myEventStealer.drainUndispatchedInputEvents();
  }

  public void dispatchAllInvocationEvents() {
    myEventStealer.dispatchAllExistingEvents();
  }

  private void paintProgress() {
    paintOverlayProgress(SwingUtilities.getRootPane(myComponent), myCreatedAt, myCancellable);
  }

  private static void paintOverlayProgress(@Nullable JRootPane rootPane, long createdAt, boolean cancellable) {
    IdeGlassPane glassPane = rootPane == null ? null : ObjectUtils.tryCast(rootPane.getGlassPane(), IdeGlassPane.class);
    if (glassPane == null) return;
    long roundedDuration = TimeoutUtil.getDurationMillis(createdAt) / 1000 * 1000;
    //noinspection HardCodedStringLiteral
    String text = (cancellable ? KeymapUtil.getShortcutText(CANCEL_SHORTCUT) + " to cancel, " : "") +
                  KeymapUtil.getShortcutText(DUMP_SHORTCUT) + " to dump threads (" +
                  NlsMessages.formatDurationApproximateNarrow(roundedDuration) + ")";
    Graphics originalGraphics = GraphicsUtil.safelyGetGraphics(rootPane);
    Rectangle viewR = rootPane.getBounds(), iconR = new Rectangle(), textR = new Rectangle();
    FontMetrics fm = originalGraphics.getFontMetrics();
    SwingUtilities.layoutCompoundLabel(fm, text, null, 0, 0, 0, 0, viewR, iconR, textR, 0);
    int border = 10;

    BufferedImage backBuffer = UIUtil.createImage(rootPane.getGraphicsConfiguration(), textR.width + 2 * border, textR.height + 2 * border,
                                                  BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND);
    Graphics graphics = backBuffer.createGraphics();
    GraphicsUtil.setupAAPainting(originalGraphics);
    graphics.setColor(JBColor.GRAY);
    graphics.fillRoundRect(0, 0, textR.width + 2 * border, textR.height + 2 * border, border, border);
    graphics.setColor(JBColor.WHITE);
    graphics.drawString(text, border, border + fm.getAscent());

    originalGraphics.translate(textR.x - border, textR.y - border);
    UIUtil.drawImage(originalGraphics, backBuffer, 0, 0, null);
    graphics.dispose();
    originalGraphics.dispose();
  }
}