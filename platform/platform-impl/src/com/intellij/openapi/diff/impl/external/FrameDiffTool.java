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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class FrameDiffTool implements DiffTool {
  public void show(DiffRequest request) {
    Collection hints = request.getHints();
    boolean shouldOpenDialog = shouldOpenDialog(hints);
    if (shouldOpenDialog) {
      final DialogBuilder builder = new DialogBuilder(request.getProject());
      DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, builder.getWindow(), builder, true);
      if (diffPanel == null) {
        Disposer.dispose(builder);
        return;
      }
      final Runnable onOkRunnable = request.getOnOkRunnable();
      if (onOkRunnable != null){
        builder.setOkOperation(new Runnable() {
          @Override
          public void run() {
            builder.getDialogWrapper().close(0);
            onOkRunnable.run();
          }
        });
      } else {
        builder.removeAllActions();
      }
      builder.setCenterPanel(diffPanel.getComponent());
      builder.setPreferredFocusComponent(diffPanel.getPreferredFocusedComponent());
      builder.setTitle(request.getWindowTitle());
      builder.setDimensionServiceKey(request.getGroupKey());

      new AnAction() {
        public void actionPerformed(final AnActionEvent e) {
          builder.getDialogWrapper().close(0);
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("CloseContent")),
                                  diffPanel.getComponent());
      showDiffDialog(builder, hints);
    }
    else {
      final FrameWrapper frameWrapper = new FrameWrapper(request.getProject(), request.getGroupKey());
      DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, frameWrapper.getFrame(), frameWrapper, true);
      if (diffPanel == null) {
        Disposer.dispose(frameWrapper);
        return;
      }
      frameWrapper.setTitle(request.getWindowTitle());
      DiffUtil.initDiffFrame(diffPanel.getProject(), frameWrapper, diffPanel, diffPanel.getComponent());

      new AnAction() {
        public void actionPerformed(final AnActionEvent e) {
          frameWrapper.getFrame().dispose();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("CloseContent")),
                                  diffPanel.getComponent());

      frameWrapper.show();
    }
  }

  /*public static void createEditableDiffFrame(final DiffRequest request) {
    final FrameWrapper frameWrapper = new FrameWrapper(request.getProject(), request.getGroupKey());
    DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, frameWrapper.getFrame(), frameWrapper);
    if (diffPanel == null) return;
    diffPanel.setPatchAppliedApproximately();

    frameWrapper.setTitle(request.getWindowTitle());

    final DialogBuilder builder = new DialogBuilder(request.getProject());
    builder.setCenterPanel(diffPanel.getComponent());
    builder.setPreferedFocusComponent(diffPanel.getPreferredFocusedComponent());

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(builder.getCenterPanel(), BorderLayout.CENTER);
    panel.add(builder.getDialogWrapper().get);

    DiffUtil.initDiffFrame(frameWrapper, diffPanel);

    new AnAction() {
      public void actionPerformed(final AnActionEvent e) {
        frameWrapper.getFrame().dispose();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("CloseContent")),
                                diffPanel.getComponent());

    frameWrapper.show();
  }*/

  @Nullable
  private DiffPanelImpl createDiffPanelIfShouldShow(DiffRequest request, Window window, @NotNull Disposable parentDisposable,
                                                           final boolean showMessage) {
    DiffPanelImpl diffPanel = createDiffPanelImpl(request, window, parentDisposable);
    if (checkNoDifferenceAndNotify(diffPanel, request, window, showMessage)) {
      Disposer.dispose(diffPanel);
      diffPanel = null;
    }
    return diffPanel;
  }

  protected DiffPanelImpl createDiffPanelImpl(@NotNull DiffRequest request, @Nullable Window window, @NotNull Disposable parentDisposable) {
    DiffPanelImpl panel = (DiffPanelImpl)DiffManagerImpl.createDiffPanel(request, window, parentDisposable, this);
    if (request.getHints().contains(DiffTool.HINT_DIFF_IS_APPROXIMATE)) {
      panel.setPatchAppliedApproximately(); // todo read only and not variants
    }
    return panel;
  }

  static void showDiffDialog(DialogBuilder builder, Collection hints) {
    builder.showModal(!hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG));
  }

  public static boolean shouldOpenDialog(Collection hints) {
    if (hints.contains(DiffTool.HINT_SHOW_MODAL_DIALOG)) return true;
    if (hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG)) return true;
    if (hints.contains(DiffTool.HINT_SHOW_FRAME)) return false;
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof JDialog;
  }

  // TODO remove check?
  private boolean checkNoDifferenceAndNotify(DiffPanel diffPanel, DiffRequest data, final Window window, final boolean showMessage) {
    if (diffPanel.hasDifferences() || data.getHints().contains(HINT_ALLOW_NO_DIFFERENCES)) {
      return false;
    }

    DiffManagerImpl manager = (DiffManagerImpl)DiffManager.getInstance();
    ComparisonPolicy oldPolicy = manager.getComparisonPolicy();
    if (oldPolicy != ComparisonPolicy.DEFAULT) {
      manager.setComparisonPolicy(ComparisonPolicy.DEFAULT);
      Disposable parentDisposable = Disposer.newDisposable();
      DiffPanel maybeDiffPanel = DiffManagerImpl.createDiffPanel(data, window, parentDisposable, this);
      manager.setComparisonPolicy(oldPolicy);

      boolean hasDiffs = maybeDiffPanel.hasDifferences();
      Disposer.dispose(parentDisposable);

      if (hasDiffs) {
        return false;
      }
    }

    return !showMessage || !askForceOpenDiff(data);
  }

  private static boolean askForceOpenDiff(DiffRequest data) {
    byte[] bytes1;
    byte[] bytes2;
    try {
      bytes1 = data.getContents()[0].getBytes();
      bytes2 = data.getContents()[1].getBytes();
    }
    catch (IOException e) {
      MessagesEx.error(data.getProject(), e.getMessage()).showNow();
      return false;
    }
    String message = Arrays.equals(bytes1, bytes2)
                     ? DiffBundle.message("diff.contents.are.identical.message.text")
                     : DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text");
    return Messages.showYesNoDialog(data.getProject(), message + "\n" + DiffBundle.message("show.diff.anyway.dialog.message"),
                                    DiffBundle.message("no.differences.dialog.title"), Messages.getQuestionIcon()) == Messages.YES;
  }

  public boolean canShow(DiffRequest data) {
    return canShowDiff(data);
  }

  public static boolean canShowDiff(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 2) return false;
    for (DiffContent content : contents) {
      if (content.isBinary()) return false;
      VirtualFile file = content.getFile();
      if (file != null && file.isDirectory()) return false;
    }
    return true;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
    return createDiffPanelImpl(request, window, parentDisposable);
  }
}
