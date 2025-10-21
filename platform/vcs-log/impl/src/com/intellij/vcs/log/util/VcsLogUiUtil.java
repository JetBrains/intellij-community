// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import com.intellij.vcs.ui.ProgressStripe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public final class VcsLogUiUtil {
  public static @NotNull JComponent installProgress(@NotNull JComponent component,
                                                    @NotNull VcsLogData logData,
                                                    @NotNull String logId,
                                                    @NotNull Disposable disposableParent) {
    ProgressStripe progressStripe = new ProgressStripe(component, disposableParent) {
      @Override
      public void updateUI() {
        super.updateUI();
        if (myDecorator != null && logData.getProgress().isRunning()) startLoadingImmediately();
      }
    };
    logData.getProgress().addProgressIndicatorListener(new VcsLogProgress.ProgressListener() {
      @Override
      public void progressChanged(@NotNull Collection<? extends VcsLogProgress.ProgressKey> keys) {
        if (isProgressVisible(keys, logId)) {
          progressStripe.startLoading();
        }
        else {
          progressStripe.stopLoading();
        }
      }
    }, disposableParent);

    return progressStripe;
  }

  public static boolean isProgressVisible(@NotNull Collection<? extends VcsLogProgress.ProgressKey> keys,
                                          @NotNull String logId) {
    if (keys.contains(VcsLogData.DATA_PACK_REFRESH)) {
      return true;
    }
    return ContainerUtil.find(keys, key -> VisiblePackRefresherImpl.isVisibleKeyFor(key, logId)) != null;
  }

  public static @NotNull JScrollPane setupScrolledGraph(@NotNull VcsLogGraphTable graphTable, int border) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(graphTable, border);
    ClientProperty.put(scrollPane, UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP);
    graphTable.viewportSet(scrollPane.getViewport());
    return scrollPane;
  }

  public static @NotNull JComponent installScrollingAndProgress(@NotNull VcsLogGraphTable table, @NotNull Disposable disposableParent) {
    JScrollPane scrollPane = setupScrolledGraph(table, SideBorder.NONE);
    JComponent tableWithProgress = installProgress(scrollPane, table.getLogData(), table.getId(), disposableParent);
    ScrollableContentBorder.setup(scrollPane, Side.TOP, tableWithProgress);
    return tableWithProgress;
  }

  public static void showTooltip(@NotNull JComponent component,
                                 @NotNull Point point,
                                 @NotNull Balloon.Position position,
                                 @NotNull @NlsContexts.Tooltip String text) {
    JEditorPane tipComponent = IdeTooltipManager.initPane(text, new HintHint(component, point).setAwtTooltip(true), null);
    IdeTooltip tooltip = new IdeTooltip(component, point, new Wrapper(tipComponent)).setPreferredPosition(position).setToCenter(false)
      .setToCenterIfSmall(false);
    IdeTooltipManager.getInstance().show(tooltip, false);
  }

  public static @NotNull History installNavigationHistory(@NotNull AbstractVcsLogUi ui, @NotNull VcsLogGraphTable table) {
    History history = new History(new VcsLogPlaceNavigator(ui));
    table.getSelectionModel().addListSelectionListener((e) -> {
      if (!history.isNavigatingNow() && !e.getValueIsAdjusting()) {
        history.pushQueryPlace();
      }
    });
    return history;
  }

  public static @NotNull @Nls String shortenTextToFit(@NotNull @Nls String text,
                                                      @NotNull FontMetrics fontMetrics,
                                                      int availableWidth,
                                                      int maxLength,
                                                      @NotNull @Nls String symbol) {
    if (fontMetrics.stringWidth(text) <= availableWidth) return text;

    for (int i = text.length(); i > maxLength; i--) {
      String result = StringUtil.shortenTextWithEllipsis(text, i, 0, symbol);
      if (fontMetrics.stringWidth(result) <= availableWidth) {
        return result;
      }
    }
    return StringUtil.shortenTextWithEllipsis(text, maxLength, 0, symbol);
  }

  public static int getHorizontalTextPadding(@NotNull SimpleColoredComponent component) {
    Insets borderInsets = component.getMyBorder().getBorderInsets(component);
    Insets ipad = component.getIpad();
    return borderInsets.left + borderInsets.right + ipad.left + ipad.right;
  }

  public static void appendActionToEmptyText(@Nls @NotNull StatusText emptyText, @Nls @NotNull String text, @NotNull Runnable action) {
    emptyText.appendSecondaryText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> action.run());
  }

  public static void appendResetFiltersActionToEmptyText(@NotNull VcsLogFilterUiEx filterUi, @Nls @NotNull StatusText emptyText) {
    appendActionToEmptyText(emptyText, VcsLogBundle.message("vcs.log.reset.filters.status.action"), filterUi::clearFilters);
  }

  public static @NotNull Dimension expandToFitToolbar(@NotNull Dimension size, @NotNull JComponent toolbar) {
    Dimension preferredSize = toolbar.getPreferredSize();
    int minToolbarSize = Math.round(Math.min(preferredSize.width, preferredSize.height) * 1.5f);
    return new Dimension(Math.max(size.width, minToolbarSize), Math.max(size.height, minToolbarSize));
  }

  public static @NotNull JComponent getComponent(@NotNull VcsLogUiEx ui) {
    if (ui.getTable() instanceof JComponent) return (JComponent)ui.getTable();
    return ui.getMainComponent();
  }

  private static final class VcsLogPlaceNavigator implements Place.Navigator {
    private static final @NonNls String PLACE_KEY = "Vcs.Log.Ui.History.PlaceKey";
    private final @NotNull AbstractVcsLogUi myUi;

    private VcsLogPlaceNavigator(@NotNull AbstractVcsLogUi ui) {
      myUi = ui;
    }

    @Override
    public void queryPlace(@NotNull Place place) {
      List<CommitId> commits = myUi.getTable().getSelection().getCommits();
      if (!commits.isEmpty()) {
        place.putPath(PLACE_KEY, commits.get(0));
      }
    }

    @Override
    public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
      if (place == null) return ActionCallback.DONE;

      Object value = place.getPath(PLACE_KEY);
      if (!(value instanceof CommitId commitId)) return ActionCallback.REJECTED;

      VcsLogUsageTriggerCollector.triggerPlaceHistoryUsed(myUi.getLogData().getProject());

      ActionCallback callback = new ActionCallback();

      ListenableFuture<Boolean> future = VcsLogNavigationUtil.jumpToCommit(myUi, commitId.getHash(), commitId.getRoot(), false, true);

      Futures.addCallback(future, new FutureCallback<>() {
        @Override
        public void onSuccess(Boolean result) {
          if (result) {
            if (requestFocus) myUi.getTable().requestFocusInWindow();
            callback.setDone();
          }
          else {
            callback.setRejected();
          }
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          callback.setRejected();
        }
      }, EdtExecutorService.getInstance());

      return callback;
    }
  }
}
