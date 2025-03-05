// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.StatusText;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.IndexSpeedSearch;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

@ApiStatus.Internal
class VcsLogMainGraphTable extends VcsLogGraphTable {
  private final @NotNull Runnable myRefresh;
  private final @NotNull VcsLogFilterUiEx myFilterUi;

  VcsLogMainGraphTable(@NotNull String logId, @NotNull GraphTableModel graphTableModel,
                       @NotNull VcsLogUiProperties uiProperties, @NotNull VcsLogColorManager colorManager,
                       @NotNull Runnable refresh,
                       @NotNull VcsLogFilterUiEx filterUi,
                       @NotNull Consumer<@NotNull String> commitByHashNavigator,
                       @NotNull Disposable disposable) {
    super(logId, graphTableModel, uiProperties, colorManager, commitByHashNavigator, disposable);
    myRefresh = refresh;
    myFilterUi = filterUi;
    IndexSpeedSearch speedSearch = new IndexSpeedSearch(getLogData().getProject(), getLogData().getIndex(), getLogData().getStorage(), this) {
      @Override
      protected boolean isSpeedSearchEnabled() {
        return Registry.is("vcs.log.speedsearch") && super.isSpeedSearchEnabled();
      }
    };
    speedSearch.setupListeners();
  }

  @Override
  protected void updateEmptyText() {
    StatusText statusText = getEmptyText();
    VisiblePack visiblePack = getModel().getVisiblePack();

    DataPackBase dataPack = visiblePack.getDataPack();
    if (dataPack instanceof DataPack.ErrorDataPack) {
      setErrorEmptyText(((DataPack.ErrorDataPack)dataPack).getError(),
                        VcsLogBundle.message("vcs.log.error.loading.commits.status"));
      appendActionToEmptyText(VcsLogBundle.message("vcs.log.refresh.status.action"),
                              () -> getLogData().refresh(getLogData().getLogProviders().keySet()));
    }
    else if (visiblePack instanceof VisiblePack.ErrorVisiblePack) {
      setErrorEmptyText(((VisiblePack.ErrorVisiblePack)visiblePack).getError(), VcsLogBundle.message("vcs.log.error.filtering.status"));
      if (visiblePack.getFilters().isEmpty()) {
        appendActionToEmptyText(VcsLogBundle.message("vcs.log.refresh.status.action"), myRefresh);
      }
      else {
        VcsLogUiUtil.appendResetFiltersActionToEmptyText(myFilterUi, getEmptyText());
      }
    }
    else if (visiblePack.getVisibleGraph().getVisibleCommitCount() == 0) {
      if (visiblePack.getFilters().isEmpty()) {
        statusText.setText(VcsLogBundle.message("vcs.log.no.commits.status")).
          appendSecondaryText(VcsLogBundle.message("vcs.log.commit.status.action"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                              ActionUtil.createActionListener(IdeActions.ACTION_CHECKIN_PROJECT, this,
                                                              ActionPlaces.UNKNOWN));
        String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHECKIN_PROJECT);
        if (!shortcutText.isEmpty()) {
          statusText.appendSecondaryText(" (" + shortcutText + ")", StatusText.DEFAULT_ATTRIBUTES, null);
        }
      }
      else {
        myFilterUi.setCustomEmptyText(getEmptyText());
      }
    }
    else {
      statusText.setText(VcsLogBundle.message("vcs.log.default.status"));
    }
  }
}
