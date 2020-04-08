// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class VcsLogTabsManager {
  @NotNull private final Project myProject;
  @NotNull private final VcsLogProjectTabsProperties myUiProperties;
  private boolean myIsLogDisposing = false;

  VcsLogTabsManager(@NotNull Project project,
                    @NotNull MessageBus messageBus,
                    @NotNull VcsLogProjectTabsProperties uiProperties,
                    @NotNull Disposable parent) {
    myProject = project;
    myUiProperties = uiProperties;

    messageBus.connect(parent).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager manager) {
        myIsLogDisposing = false;
        createLogTabs(manager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager manager) {
        myIsLogDisposing = true;
      }
    });
  }

  @CalledInAwt
  private void createLogTabs(@NotNull VcsLogManager manager) {
    myUiProperties.getTabs().forEach((id, kind) -> openLogTab(manager, id, kind, false, null));
  }

  // for statistics
  @NotNull
  public Collection<String> getTabs() {
    return myUiProperties.getTabs().keySet();
  }

  @NotNull
  MainVcsLogUi openAnotherLogTab(@NotNull VcsLogManager manager, @Nullable VcsLogFilterCollection filters,
                                 @NotNull VcsLogManager.LogWindowKind kind) {
    return openLogTab(manager, generateTabId(myProject), kind, true, filters);
  }

  @NotNull
  private MainVcsLogUi openLogTab(@NotNull VcsLogManager manager, @NotNull String tabId, @NotNull VcsLogManager.LogWindowKind kind,
                                  boolean focus, @Nullable VcsLogFilterCollection filters) {
    if (filters != null) myUiProperties.resetState(tabId);

    VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> factory =
      new PersistentVcsLogUiFactory(manager.getMainLogUiFactory(tabId, filters), kind);

    MainVcsLogUi ui;
    if (kind == VcsLogManager.LogWindowKind.EDITOR) {
      ui = VcsLogEditorUtilKt.openLogTab(myProject, manager, getFullName(tabId), factory, focus);
      ui.addFilterListener(() -> {
        VcsLogEditorUtilKt.updateTabName(myProject, ui);
      });
    }
    else if (kind == VcsLogManager.LogWindowKind.TOOL_WINDOW) {
      ui = VcsLogContentUtil.openLogTab(myProject, manager, VcsLogContentProvider.TAB_NAME,
                                        () -> VcsLogBundle.message("vcs.log.tab.name"), u -> generateShortDisplayName(u),
                                        factory, focus);
      ui.addFilterListener(() -> VcsLogContentUtil.updateLogUiName(myProject, ui));
    }
    else {
      throw new UnsupportedOperationException("Only log in editor or tool window is supported");
    }
    return ui;
  }

  @NotNull
  private static String generateShortDisplayName(@NotNull VcsLogUi ui) {
    VcsLogFilterCollection filters = ui.getFilterUi().getFilters();
    if (filters.isEmpty()) return "";
    return StringUtil.shortenTextWithEllipsis(VcsLogFiltersKt.getPresentation(filters), 150, 20);
  }

  @NotNull
  private static String getFullName(@NotNull String shortName) {
    return ContentUtilEx.getFullName(VcsLogBundle.message("vcs.log.tab.name"), shortName);
  }

  @NotNull
  public static String generateDisplayName(@NotNull VcsLogUi ui) {
    return getFullName(generateShortDisplayName(ui));
  }

  @NotNull
  private static String generateTabId(@NotNull Project project) {
    Set<String> existingIds = ContainerUtil.union(VcsLogContentUtil.getExistingLogIds(project),
                                                  VcsLogEditorUtilKt.getExistingLogIds(project));
    for (int i = 1; ; i++) {
      String idString = Integer.toString(i);
      if (!existingIds.contains(idString)) {
        return idString;
      }
    }
  }

  private class PersistentVcsLogUiFactory implements VcsLogManager.VcsLogUiFactory<MainVcsLogUi> {
    @NotNull private final VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> myFactory;
    @NotNull private final VcsLogManager.LogWindowKind myLogWindowKind;

    PersistentVcsLogUiFactory(@NotNull VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> factory,
                              @NotNull VcsLogManager.LogWindowKind kind) {
      myFactory = factory;
      myLogWindowKind = kind;
    }

    @Override
    public MainVcsLogUi createLogUi(@NotNull Project project,
                                    @NotNull VcsLogData logData) {
      MainVcsLogUi ui = myFactory.createLogUi(project, logData);
      myUiProperties.addTab(ui.getId(), myLogWindowKind);
      Disposer.register(ui, () -> {
        if (Disposer.isDisposing(myProject) || myIsLogDisposing) return; // need to restore the tab after project/log is recreated

        myUiProperties.removeTab(ui.getId()); // tab is closed by a user
      });
      return ui;
    }
  }
}
