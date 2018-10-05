// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Functions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.ui.frame.DetailsPanel;
import com.intellij.vcs.log.ui.frame.ProgressStripe;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.actionSystem.AnAction.ACTIONS_KEY;

public class VcsLogUiUtil {
  @NotNull
  public static JComponent installProgress(@NotNull JComponent component,
                                           @NotNull VcsLogData logData,
                                           @NotNull String logId,
                                           @NotNull Disposable disposableParent) {
    ProgressStripe progressStripe =
      new ProgressStripe(component, disposableParent, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
        @Override
        public void updateUI() {
          super.updateUI();
          if (myDecorator != null && logData.getProgress().isRunning()) startLoadingImmediately();
        }
      };
    logData.getProgress().addProgressIndicatorListener(new VcsLogProgress.ProgressListener() {
      @Override
      public void progressStarted(@NotNull Collection<VcsLogProgress.ProgressKey> keys) {
        if (matches(keys)) {
          progressStripe.startLoading();
        }
      }

      @Override
      public void progressChanged(@NotNull Collection<VcsLogProgress.ProgressKey> keys) {
        if (matches(keys)) {
          progressStripe.startLoading();
        }
        else {
          progressStripe.stopLoading();
        }
      }

      @Override
      public void progressStopped() {
        progressStripe.stopLoading();
      }

      private boolean matches(@NotNull Collection<VcsLogProgress.ProgressKey> keys) {
        if (keys.contains(VcsLogData.DATA_PACK_REFRESH)) {
          return true;
        }
        return ContainerUtil.find(keys, key -> VisiblePackRefresherImpl.isVisibleKeyFor(key, logId)) != null;
      }
    }, disposableParent);

    return progressStripe;
  }

  @NotNull
  public static JScrollPane setupScrolledGraph(@NotNull VcsLogGraphTable graphTable, int border) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(graphTable, border);
    graphTable.viewportSet(scrollPane.getViewport());
    return scrollPane;
  }

  public static void installDetailsListeners(@NotNull VcsLogGraphTable graphTable,
                                             @NotNull DetailsPanel detailsPanel,
                                             @NotNull VcsLogData logData,
                                             @NotNull Disposable disposableParent) {
    Runnable miniDetailsLoadedListener = () -> {
      graphTable.reLayout();
      graphTable.repaint();
    };
    Runnable containingBranchesListener = () -> {
      detailsPanel.branchesChanged();
      graphTable.repaint(); // we may need to repaint highlighters
    };
    logData.getMiniDetailsGetter().addDetailsLoadedListener(miniDetailsLoadedListener);
    logData.getContainingBranchesGetter().addTaskCompletedListener(containingBranchesListener);

    Disposer.register(disposableParent, () -> {
      logData.getContainingBranchesGetter().removeTaskCompletedListener(containingBranchesListener);
      logData.getMiniDetailsGetter().removeDetailsLoadedListener(miniDetailsLoadedListener);
    });
  }

  @NotNull
  public static SimpleTextAttributes getLinkAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.link());
  }

  public static void showTooltip(@NotNull JComponent component,
                                 @NotNull Point point,
                                 @NotNull Balloon.Position position,
                                 @NotNull String text) {
    JEditorPane tipComponent = IdeTooltipManager.initPane(text, new HintHint(component, point).setAwtTooltip(true), null);
    IdeTooltip tooltip = new IdeTooltip(component, point, new Wrapper(tipComponent)).setPreferredPosition(position).setToCenter(false)
                                                                                    .setToCenterIfSmall(false);
    IdeTooltipManager.getInstance().show(tooltip, false);
  }

  public static void installScrollingActions(@NotNull JTable component, @NotNull KeyboardShortcut... conflictingShortcuts) {
    ScrollingUtil.installActions(component, false);

    // remove shortcuts that conflict with go to child/parent
    List<KeyboardShortcut> shortcuts = ContainerUtil.mapNotNull(conflictingShortcuts, Functions.id());
    List<KeyStroke> strokes = ContainerUtil.mapNotNull(shortcuts, shortcut -> shortcut.getFirstKeyStroke());

    strokes.forEach(stroke -> component.getInputMap(JComponent.WHEN_FOCUSED).remove(stroke));
    for (AnAction action : ContainerUtil.newArrayList(UIUtil.getClientProperty(component, ACTIONS_KEY))) {
      if (!getMatchingShortcut(action, shortcuts).isEmpty()) {
        action.unregisterCustomShortcutSet(component);
      }
    }
  }

  @NotNull
  private static Collection<Shortcut> getMatchingShortcut(@NotNull AnAction action, @NotNull List<KeyboardShortcut> shortcuts) {
    return ContainerUtil.intersection(Arrays.asList(action.getShortcutSet().getShortcuts()), shortcuts);
  }
}
