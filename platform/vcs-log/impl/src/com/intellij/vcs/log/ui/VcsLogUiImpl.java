// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class VcsLogUiImpl extends CommonVcsLogUiImpl implements MainVcsLogUi {
  private final @NotNull MainFrame myMainFrame;

  public VcsLogUiImpl(@NotNull String id,
                      @NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher,
                      @Nullable VcsLogFilterCollection initialFilters) {
    this(id, logData, manager, uiProperties, refresher, initialFilters, true);
  }

  public VcsLogUiImpl(@NotNull String id,
                      @NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher,
                      @Nullable VcsLogFilterCollection initialFilters,
                      boolean isEditorDiffPreview) {
    super(id, logData, manager, uiProperties, refresher);

    VcsLogFilterUiEx filterUi = createFilterUi(filters -> applyFiltersAndUpdateUi(filters), initialFilters, this);
    myMainFrame = createMainFrame(logData, uiProperties, filterUi, isEditorDiffPreview);

    LOG_HIGHLIGHTER_FACTORY_EP.addChangeListener(this::updateHighlighters, this);
    ApplicationManager.getApplication().invokeLater(this::updateHighlighters, o -> myDisposableFlag.isDisposed());

    VcsLogUiProperties.PropertiesChangeListener detailsStateListener = new VcsLogUiProperties.PropertiesChangeListener() {
      @Override
      public <T> void onPropertyChanged(VcsLogUiProperties.@NotNull VcsLogUiProperty<T> property) {
        if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
          myMainFrame.showDetails(uiProperties.get(CommonUiProperties.SHOW_DETAILS));
        }
      }
    };
    uiProperties.addChangeListener(detailsStateListener, this);

    applyFiltersAndUpdateUi(myMainFrame.getFilterUi().getFilters());
  }

  protected @NotNull MainFrame createMainFrame(@NotNull VcsLogData logData,
                                               @NotNull MainVcsLogUiProperties uiProperties,
                                               @NotNull VcsLogFilterUiEx filterUi,
                                               boolean isEditorDiffPreview) {
    return new MainFrame(logData, this, uiProperties, filterUi, myColorManager, isEditorDiffPreview, this);
  }

  protected @NotNull VcsLogFilterUiEx createFilterUi(@NotNull Consumer<VcsLogFilterCollection> filterConsumer,
                                                     @Nullable VcsLogFilterCollection filters,
                                                     @NotNull Disposable parentDisposable) {
    return new VcsLogClassicFilterUi(myLogData, filterConsumer, getProperties(), myColorManager, filters, parentDisposable);
  }

  @Override
  protected void updateDataPack(boolean permGraphChanged) {
    myMainFrame.updateDataPack(myVisiblePack, permGraphChanged);
  }

  protected @NotNull MainFrame getMainFrame() {
    return myMainFrame;
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    if (getFilterUi().getFilters().isEmpty() || !commitExists) {
      super.handleCommitNotFound(commitId, commitExists, rowGetter);
      return;
    }

    List<NotificationAction> actions = new ArrayList<>();
    actions.add(NotificationAction.createSimple(VcsLogBundle.message("vcs.log.commit.does.not.match.view.and.reset.link"), () -> {
      getFilterUi().clearFilters();
      VcsLogUtil.invokeOnChange(this, () -> jumpTo(commitId, rowGetter, SettableFuture.create(), false, true),
                                pack -> pack.getFilters().isEmpty());
    }));
    VcsProjectLog projectLog = VcsProjectLog.getInstance(myProject);
    if (projectLog.getDataManager() == myLogData) {
      actions.add(NotificationAction.createSimple(VcsLogBundle.message("vcs.log.commit.does.not.match.view.in.tab.link"), () -> {
        MainVcsLogUi ui = projectLog.openLogTab(VcsLogFilterObject.collection());
        if (ui != null) {
          VcsLogUtil.invokeOnChange(ui, () -> ui.jumpTo(commitId, rowGetter, SettableFuture.create(), false, true),
                                    pack -> pack.getFilters().isEmpty());
        }
      }));
    }
    VcsNotifier.getInstance(myProject).notifyWarning(VcsLogNotificationIdsHolder.COMMIT_NOT_FOUND, "",
                                                     getCommitNotFoundMessage(commitId, true),
                                                     actions.toArray(NotificationAction[]::new));
  }

  @Override
  public @NotNull VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @Override
  public @NotNull JComponent getMainComponent() {
    return myMainFrame;
  }

  @Override
  public @NotNull VcsLogFilterUiEx getFilterUi() {
    return myMainFrame.getFilterUi();
  }

  @Override
  public @NotNull ChangesBrowserBase getChangesBrowser() {
    return myMainFrame.getChangesBrowser();
  }

  @Override
  public @NotNull JComponent getToolbar() {
    return myMainFrame.getToolbar();
  }

  @Override
  public void selectFilePath(@NotNull FilePath filePath, boolean requestFocus) {
    getMainFrame().selectFilePath(filePath, requestFocus);
  }
}
