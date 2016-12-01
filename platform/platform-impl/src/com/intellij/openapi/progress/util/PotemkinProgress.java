/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A progress indicator for processes running in EDT. Paints itself in checkCanceled calls.
 *
 * @author peter
 */
public class PotemkinProgress extends ProgressWindow {
  private final BufferedImage myScreenshot;
  private long myLastUiUpdate = System.currentTimeMillis();

  public PotemkinProgress(@NotNull String title, @Nullable Project project, @Nullable JComponent parentComponent) {
    super(false,false, project, parentComponent, null);
    setTitle(title);

    ProgressDialog dialog = getDialog();
    Window parentWindow = dialog == null ? null : dialog.getParentWindow();
    myScreenshot = parentWindow instanceof IdeFrame ? takeScreenshot(((IdeFrame)parentWindow).getComponent()) : null;

    installCheckCanceledPaintingHook();
  }

  /**
   * Remember how everything looked. We must not call custom paint methods during write action,
   * because they might access the model which might be inconsistent at that moment.
   */
  @NotNull
  private static BufferedImage takeScreenshot(@NotNull JComponent component) {
    BufferedImage image = UIUtil.createImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    component.paint(graphics);
    graphics.dispose();
    return image;
  }

  private void installCheckCanceledPaintingHook() {
    // make ProgressManager#checkCanceled actually delegate to the current indicator
    HeavyProcessLatch.INSTANCE.prioritizeUiActivity();

    // isCanceled is final, so using a nonstandard way of plugging into it
    addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public boolean isCanceled() {
        updateUI();
        return super.isCanceled();
      }
    });
  }

  private void updateUI() {
    ProgressDialog dialog = getDialog();
    if (!ApplicationManager.getApplication().isDispatchThread() || dialog == null) return;

    JRootPane rootPane = dialog.getPanel().getRootPane();
    if (rootPane == null) {
      rootPane = considerShowingDialog(dialog);
    }

    if (rootPane != null && timeToPaint()) {
      paintProgress(rootPane, dialog);
    }
  }

  @Nullable
  private JRootPane considerShowingDialog(@NotNull ProgressDialog dialog) {
    if (System.currentTimeMillis() - myLastUiUpdate > DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      dialog.myRepaintRunnable.run();
      showDialog();
      return dialog.getPanel().getRootPane();
    }
    return null;
  }

  private boolean timeToPaint() {
    long now = System.currentTimeMillis();
    if (now - myLastUiUpdate <= ProgressDialog.UPDATE_INTERVAL) {
      return false;
    }
    myLastUiUpdate = now;
    return true;
  }

  private void paintProgress(@NotNull JRootPane rootPane, @NotNull ProgressDialog dialog) {
    dialog.myRepaintRunnable.run();

    JPanel dialogPanel = dialog.getPanel();
    if (myScreenshot != null) {
      IdeGlassPaneImpl glassPane = (IdeGlassPaneImpl)IdeGlassPaneUtil.find(rootPane);
      glassPane.activateIfNeeded();
      glassPane.validate();

      rootPane.getGraphics().drawImage(paintToBuffer(rootPane, dialogPanel), 0, 0, null);
    } else {
      dialogPanel.validate();
      dialogPanel.paintImmediately(dialogPanel.getBounds());
    }
  }

  @NotNull
  private Image paintToBuffer(JRootPane rootPane, JPanel dialogPanel) {
    Image buffer = rootPane.createVolatileImage(rootPane.getWidth(), rootPane.getHeight());

    Graphics g = buffer.getGraphics();
    assert myScreenshot != null;
    UIUtil.drawImage(g, myScreenshot, null, 0, 0);

    Container container = SwingUtilities.getAncestorOfClass(DialogWrapperDialog.class, dialogPanel);
    assert container instanceof JComponent;
    g.translate(container.getX() - rootPane.getX(), container.getY() - rootPane.getY());
    container.paint(g);
    g.dispose();
    return buffer;
  }
}
