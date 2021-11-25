// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.DiffPreview;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.ChangesBrowserToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ChangeListViewerDialog extends DialogWrapper {
  private static final String CHANGES_DETAILS_WINDOW_KEY = "CommittedChangesDetailsLock";
  public static final String DIMENSION_SERVICE_KEY = "VCS.ChangeListViewerDialog";

  public static void show(@NotNull Project project,
                          @Nullable @Nls String title,
                          @NotNull LoadingCommittedChangeListPanel loadingPanel) {
    show(project, title, loadingPanel, null, AbstractVcsHelperImpl.showCommittedChangesAsTab());
  }

  public static void show(@NotNull Project project,
                          @Nullable @Nls String title,
                          @NotNull LoadingCommittedChangeListPanel loadingPanel,
                          @Nullable BackgroundableActionLock lock,
                          boolean showAsTab) {
    if (showAsTab) {
      showAsTab(project, title, loadingPanel);
    }
    else {
      showDialog(project, title, loadingPanel, lock);
    }
  }

  private static void showAsTab(@NotNull Project project,
                                @Nullable @NlsContexts.TabTitle String title,
                                @NotNull LoadingCommittedChangeListPanel loadingPanel) {
    loadingPanel.hideSideBorders();

    SimpleChangesBrowser changesBrowser = loadingPanel.getChangesBrowser();
    DiffPreview diffPreview = ChangesBrowserToolWindow.createDiffPreview(project, changesBrowser, loadingPanel);
    changesBrowser.setShowDiffActionPreview(diffPreview);

    Content content = ContentFactory.SERVICE.getInstance().createContent(loadingPanel.getContent(), title, false);
    content.setPreferredFocusableComponent(loadingPanel.getPreferredFocusedComponent());
    content.setDisposer(loadingPanel);
    ChangesBrowserToolWindow.showTab(project, content);
  }

  public static void showDialog(@NotNull Project project,
                                @Nullable @NlsContexts.DialogTitle String title,
                                @NotNull LoadingCommittedChangeListPanel loadingPanel) {
    showDialog(project, title, loadingPanel, null);
  }

  private static void showDialog(@NotNull Project project,
                                 @Nullable @NlsContexts.DialogTitle String title,
                                 @NotNull LoadingCommittedChangeListPanel loadingPanel,
                                 @Nullable BackgroundableActionLock lock) {
    ChangeListViewerDialog dlg = new ChangeListViewerDialog(project, loadingPanel);
    if (title != null) {
      dlg.setTitle(title);
    }

    if (lock != null) {
      lock.lock();
      UIUtil.putWindowClientProperty(dlg.getWindow(), CHANGES_DETAILS_WINDOW_KEY, lock);
      Disposer.register(dlg.getDisposable(), () -> lock.unlock());
    }

    dlg.show();
  }

  public static boolean tryFocusExistingDialog(@Nullable BackgroundableActionLock lock) {
    if (lock == null || !lock.isLocked()) return false;

    for (Window window : Window.getWindows()) {
      Object windowLock = UIUtil.getWindowClientProperty(window, CHANGES_DETAILS_WINDOW_KEY);
      if (windowLock != null && lock.equals(windowLock)) {
        UIUtil.toFront(window);
        return true;
      }
    }
    return true; // lock is being held, do not show another dialog anyway
  }


  @NotNull private final LoadingCommittedChangeListPanel myLoadingPanel;

  public ChangeListViewerDialog(@NotNull Project project,
                                @NotNull LoadingCommittedChangeListPanel loadingPanel) {
    this(project, null, loadingPanel);
  }

  public ChangeListViewerDialog(@NotNull Project project,
                                @Nullable Component parentComponent,
                                @NotNull LoadingCommittedChangeListPanel loadingPanel) {
    super(project, parentComponent, true, IdeModalityType.IDE);

    myLoadingPanel = loadingPanel;
    Disposer.register(getDisposable(), myLoadingPanel);

    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.message("close.action.name"));
    setModal(false);

    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_SERVICE_KEY;
  }

  @Override
  public JComponent createCenterPanel() {
    return myLoadingPanel.getContent();
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action cancelAction = getCancelAction();
    cancelAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    return new Action[]{cancelAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLoadingPanel.getPreferredFocusedComponent();
  }
}
