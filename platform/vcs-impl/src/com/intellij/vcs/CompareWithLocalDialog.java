// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.DiffPreview;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.browser.LoadingChangesPanel;
import com.intellij.openapi.vcs.history.actions.GetVersionAction;
import com.intellij.openapi.vcs.history.actions.GetVersionAction.FileRevisionProvider;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.openapi.vcs.impl.ChangesBrowserToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class CompareWithLocalDialog {
  @RequiresEdt
  public static void showChanges(@NotNull Project project,
                                 @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                 @NotNull LocalContent localContent,
                                 @NotNull ThrowableComputable<? extends Collection<Change>, ? extends VcsException> changesLoader) {
    if (localContent != LocalContent.NONE) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    if (AbstractVcsHelperImpl.showCommittedChangesAsTab()) {
      showAsTab(project, dialogTitle, localContent, changesLoader);
    }
    else {
      showDialog(project, dialogTitle, localContent, changesLoader);
    }
  }

  private static void showDialog(@NotNull Project project,
                                 @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                 @NotNull LocalContent localContent,
                                 @NotNull ThrowableComputable<? extends Collection<Change>, ? extends VcsException> changesLoader) {
    MyLoadingChangesPanel changesPanel = createPanel(project, localContent, changesLoader);

    DialogBuilder dialogBuilder = new DialogBuilder(project);
    dialogBuilder.setTitle(dialogTitle);
    dialogBuilder.setActionDescriptors(new DialogBuilder.CloseDialogAction());
    dialogBuilder.setCenterPanel(changesPanel);
    dialogBuilder.setPreferredFocusComponent(changesPanel.getChangesBrowser().getPreferredFocusedComponent());
    dialogBuilder.addDisposable(changesPanel);
    dialogBuilder.setDimensionServiceKey("Git.DiffForPathsDialog");
    dialogBuilder.showNotModal();
  }

  private static void showAsTab(@NotNull Project project,
                                @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                @NotNull LocalContent localContent,
                                @NotNull ThrowableComputable<? extends Collection<Change>, ? extends VcsException> changesLoader) {
    MyLoadingChangesPanel changesPanel = createPanel(project, localContent, changesLoader);

    ChangesBrowserBase changesBrowser = changesPanel.getChangesBrowser();
    DiffPreview diffPreview = ChangesBrowserToolWindow.createDiffPreview(project, changesBrowser, changesPanel);
    changesBrowser.setShowDiffActionPreview(diffPreview);

    Content content = ContentFactory.getInstance().createContent(changesPanel, dialogTitle, false);
    content.setPreferredFocusableComponent(changesBrowser.getPreferredFocusedComponent());
    content.setDisposer(changesPanel);

    ChangesBrowserToolWindow.showTab(project, content);
  }

  @NotNull
  private static MyLoadingChangesPanel createPanel(
    @NotNull Project project,
    @NotNull LocalContent localContent,
    @NotNull ThrowableComputable<? extends Collection<Change>, ? extends VcsException> changesLoader
  ) {
    MyChangesBrowser changesBrowser = new MyChangesBrowser(project, localContent);
    MyLoadingChangesPanel changesPanel = new MyLoadingChangesPanel(changesBrowser) {
      @NotNull
      @Override
      protected Collection<Change> loadChanges() throws VcsException {
        return changesLoader.compute();
      }
    };
    Disposer.register(changesPanel, changesBrowser);

    changesPanel.reloadChanges();
    return changesPanel;
  }

  private static abstract class MyLoadingChangesPanel extends JPanel implements UiDataProvider, Disposable {
    public static final DataKey<MyLoadingChangesPanel> DATA_KEY = DataKey.create("git4idea.log.MyLoadingChangesPanel");

    private final SimpleAsyncChangesBrowser myChangesBrowser;
    private final LoadingChangesPanel myLoadingPanel;

    private MyLoadingChangesPanel(@NotNull SimpleAsyncChangesBrowser changesBrowser) {
      super(new BorderLayout());

      myChangesBrowser = changesBrowser;

      myLoadingPanel = new LoadingChangesPanel(myChangesBrowser, this);
      add(myLoadingPanel, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {
    }

    @NotNull
    public ChangesBrowserBase getChangesBrowser() {
      return myChangesBrowser;
    }

    public void reloadChanges() {
      myLoadingPanel.loadChangesInBackground(this::loadChanges, this::applyResult);
    }

    @NotNull
    protected abstract Collection<Change> loadChanges() throws VcsException;

    private void applyResult(@Nullable Collection<? extends Change> changes) {
      myChangesBrowser.setChangesToDisplay(changes != null ? changes : Collections.emptyList());
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(DATA_KEY, this);
    }
  }

  private static class MyChangesBrowser extends SimpleAsyncChangesBrowser implements Disposable {
    @NotNull private final CompareWithLocalDialog.LocalContent myLocalContent;

    private MyChangesBrowser(@NotNull Project project, @NotNull LocalContent localContent) {
      super(project, false, true);
      myLocalContent = localContent;

      hideViewerBorder();
      myViewer.setTreeStateStrategy(ChangesTree.KEEP_NON_EMPTY);
    }

    @Override
    public void dispose() {
      shutdown();
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      List<AnAction> actions = new ArrayList<>();
      actions.add(new MyRefreshAction());
      actions.addAll(super.createToolbarActions());
      actions.add(ActionManager.getInstance().getAction("Vcs.GetVersion"));
      return actions;
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        ActionManager.getInstance().getAction("ChangesView.CreatePatchFromChanges"),
        ActionManager.getInstance().getAction("Vcs.GetVersion")
      );
    }
  }

  public static class GetVersionActionProvider implements AnActionExtensionProvider {
    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      return e.getData(MyLoadingChangesPanel.DATA_KEY) != null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      MyLoadingChangesPanel changesPanel = e.getData(MyLoadingChangesPanel.DATA_KEY);
      if (project == null || changesPanel == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      MyChangesBrowser browser = ObjectUtils.tryCast(changesPanel.getChangesBrowser(), MyChangesBrowser.class);
      boolean isVisible = browser != null && browser.myLocalContent != LocalContent.NONE;
      boolean isEnabled = isVisible && !browser.getSelectedChanges().isEmpty();
      e.getPresentation().setVisible(isVisible);
      e.getPresentation().setEnabled(isEnabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = Objects.requireNonNull(e.getProject());
      MyLoadingChangesPanel changesPanel = e.getData(MyLoadingChangesPanel.DATA_KEY);
      if (changesPanel == null) return;
      MyChangesBrowser browser = (MyChangesBrowser)changesPanel.getChangesBrowser();

      List<FileRevisionProvider> fileContentProviders = ContainerUtil.map(browser.getSelectedChanges(), change -> {
        return new MyFileContentProvider(change, browser.myLocalContent);
      });
      GetVersionAction.doGet(project, VcsBundle.message("activity.name.get"), fileContentProviders,
                             () -> {
                               FileDocumentManager.getInstance().saveAllDocuments();
                               changesPanel.reloadChanges();
                             });
    }

    private static class MyFileContentProvider implements FileRevisionProvider {
      @NotNull private final Change myChange;
      @NotNull private final CompareWithLocalDialog.LocalContent myLocalContent;

      private MyFileContentProvider(@NotNull Change change,
                                    @NotNull LocalContent localContent) {
        myChange = change;
        myLocalContent = localContent;
      }

      @NotNull
      @Override
      public FilePath getFilePath() {
        return ChangesUtil.getFilePath(myChange);
      }

      @Override
      public @Nullable GetVersionAction.FileRevisionContent getContent() throws VcsException {
        ContentRevision revision = myLocalContent == LocalContent.AFTER ? myChange.getBeforeRevision()
                                                                        : myChange.getAfterRevision();
        if (revision == null) return null;
        byte[] bytes = ChangesUtil.loadContentRevision(revision);

        FilePath oldFilePath = myChange.isMoved() || myChange.isRenamed() ? revision.getFile() : null;
        return new GetVersionAction.FileRevisionContent(bytes, oldFilePath);
      }
    }
  }

  private static class MyRefreshAction extends DumbAwareAction {
    private MyRefreshAction() {
      super(VcsBundle.messagePointer("action.name.refresh.compare.with.local.panel"),
            VcsBundle.messagePointer("action.description.refresh.compare.with.local.panel"),
            AllIcons.Actions.Refresh);
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_REFRESH));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      MyLoadingChangesPanel changesPanel = e.getData(MyLoadingChangesPanel.DATA_KEY);
      if (changesPanel == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      MyChangesBrowser browser = ObjectUtils.tryCast(changesPanel.getChangesBrowser(), MyChangesBrowser.class);
      e.getPresentation().setEnabledAndVisible(browser != null && browser.myLocalContent != LocalContent.NONE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      MyLoadingChangesPanel changesPanel = e.getData(MyLoadingChangesPanel.DATA_KEY);
      if (changesPanel == null) return;
      FileDocumentManager.getInstance().saveAllDocuments();
      changesPanel.reloadChanges();
    }
  }

  public enum LocalContent {BEFORE, AFTER, NONE}
}
