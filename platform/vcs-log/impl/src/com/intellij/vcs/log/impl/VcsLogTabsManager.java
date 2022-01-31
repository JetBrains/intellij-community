// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.TabGroupId;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.editor.VcsLogVirtualFileSystem;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class VcsLogTabsManager {
  private static final Logger LOG = Logger.getInstance(VcsLogTabsManager.class);
  private static final TabGroupId TAB_GROUP_ID = new TabGroupId(VcsLogContentProvider.TAB_NAME,
                                                                () -> VcsLogBundle.message("vcs.log.tab.name"), true);
  @NotNull private final Project myProject;
  @NotNull private final VcsLogProjectTabsProperties uiProperties;
  private boolean isLogDisposing = false;

  VcsLogTabsManager(@NotNull Project project,
                    @NotNull VcsLogProjectTabsProperties uiProperties,
                    @NotNull Disposable parent) {
    myProject = project;
    this.uiProperties = uiProperties;

    project.getMessageBus().connect(parent).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager manager) {
        isLogDisposing = false;
        Map<String, VcsLogTabLocation> savedTabs = VcsLogTabsManager.this.uiProperties.getTabs();
        if (savedTabs.isEmpty()) {
          return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          if (LOG.assertTrue(!manager.isDisposed(), "Attempting to open tabs on disposed VcsLogManager")) {
            reopenLogTabs(manager, savedTabs);
          }
        }, ModalityState.NON_MODAL, o -> project.isDisposed() || manager != VcsProjectLog.getInstance(project).getLogManager());
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager manager) {
        isLogDisposing = true;
      }
    });
  }

  @RequiresEdt
  private void reopenLogTabs(@NotNull VcsLogManager manager, @NotNull Map<String, VcsLogTabLocation> tabs) {
    tabs.forEach((id, location) -> {
      if (location == VcsLogTabLocation.EDITOR) {
        openEditorLogTab(id, false, null);
      }
      else if (location == VcsLogTabLocation.TOOL_WINDOW) {
        openToolWindowLogTab(manager, id, false, null);
      }
      else {
        LOG.warn("Unsupported log tab location " + location);
      }
    });
  }

  // for statistics
  @NotNull
  public Collection<String> getTabs() {
    return uiProperties.getTabs().keySet();
  }

  @NotNull
  MainVcsLogUi openAnotherLogTab(@NotNull VcsLogManager manager, @NotNull VcsLogFilterCollection filters,
                                 @NotNull VcsLogTabLocation location) {
    String tabId = generateTabId(manager);
    uiProperties.resetState(tabId);
    if (location == VcsLogTabLocation.EDITOR) {
      FileEditor[] editors = openEditorLogTab(tabId, true, filters);
      return Objects.requireNonNull(VcsLogEditorUtil.findVcsLogUi(editors, MainVcsLogUi.class));
    }
    else if (location == VcsLogTabLocation.TOOL_WINDOW) {
      return openToolWindowLogTab(manager, tabId, true, filters);
    }
    throw new UnsupportedOperationException("Only log in editor or tool window is supported");
  }

  private FileEditor @NotNull [] openEditorLogTab(@NotNull String tabId, boolean focus, @Nullable VcsLogFilterCollection filters) {
    VirtualFile file = VcsLogVirtualFileSystem.getInstance().createVcsLogFile(myProject, tabId, filters);
    return FileEditorManager.getInstance(myProject).openFile(file, focus, true);
  }

  @NotNull
  private MainVcsLogUi openToolWindowLogTab(@NotNull VcsLogManager manager,
                                            @NotNull String tabId,
                                            boolean focus,
                                            @Nullable VcsLogFilterCollection filters) {
    VcsLogManager.VcsLogUiFactory<MainVcsLogUi> factory = getPersistentVcsLogUiFactory(manager, tabId,
                                                                                       VcsLogTabLocation.TOOL_WINDOW,
                                                                                       filters);
    MainVcsLogUi ui = VcsLogContentUtil.openLogTab(myProject, manager, TAB_GROUP_ID, u -> generateShortDisplayName(u), factory, focus);
    ui.getFilterUi().addFilterListener(() -> VcsLogContentUtil.updateLogUiName(myProject, ui));
    return ui;
  }

  @RequiresEdt
  @ApiStatus.Internal
  public VcsLogManager.VcsLogUiFactory<MainVcsLogUi> getPersistentVcsLogUiFactory(@NotNull VcsLogManager manager,
                                                                                  @NotNull String tabId,
                                                                                  @NotNull VcsLogTabLocation location,
                                                                                  @Nullable VcsLogFilterCollection filters) {
    return new PersistentVcsLogUiFactory(manager.getMainLogUiFactory(tabId, filters), location);
  }

  @NotNull
  @NlsContexts.TabTitle
  private static String generateShortDisplayName(@NotNull VcsLogUi ui) {
    VcsLogFilterCollection filters = ui.getFilterUi().getFilters();
    if (filters.isEmpty()) return "";
    return StringUtil.shortenTextWithEllipsis(VcsLogFiltersKt.getPresentation(filters), 150, 20);
  }

  @NotNull
  @NlsContexts.TabTitle
  public static String getFullName(@NotNull @NlsContexts.TabTitle String shortName) {
    return ContentUtilEx.getFullName(VcsLogBundle.message("vcs.log.tab.name"), shortName);
  }

  @NotNull
  @NlsContexts.TabTitle
  public static String generateDisplayName(@NotNull VcsLogUi ui) {
    return getFullName(generateShortDisplayName(ui));
  }

  @NotNull
  @NonNls
  private static String generateTabId(@NotNull VcsLogManager manager) {
    Set<String> existingIds = ContainerUtil.map2Set(manager.getLogUis(), VcsLogUi::getId);

    String newId;
    do {
      newId = UUID.randomUUID().toString();
    }
    while (existingIds.contains(newId));

    return newId;
  }

  private class PersistentVcsLogUiFactory implements VcsLogManager.VcsLogUiFactory<MainVcsLogUi> {
    @NotNull private final VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> myFactory;
    @NotNull private final VcsLogTabLocation myLogTabLocation;

    PersistentVcsLogUiFactory(@NotNull VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> factory,
                              @NotNull VcsLogTabLocation location) {
      myFactory = factory;
      myLogTabLocation = location;
    }

    @Override
    public MainVcsLogUi createLogUi(@NotNull Project project, @NotNull VcsLogData logData) {
      MainVcsLogUi ui = myFactory.createLogUi(project, logData);
      uiProperties.addTab(ui.getId(), myLogTabLocation);
      Disposer.register(ui, () -> {
        if (myProject.isDisposed() || isLogDisposing) return; // need to restore the tab after project/log is recreated

        uiProperties.removeTab(ui.getId()); // tab is closed by a user
      });
      return ui;
    }
  }
}
