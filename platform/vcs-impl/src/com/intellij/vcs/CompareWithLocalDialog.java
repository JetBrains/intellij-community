// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
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
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.browser.LoadingChangesPanel;
import com.intellij.openapi.vcs.history.actions.GetVersionAction;
import com.intellij.openapi.vcs.history.actions.GetVersionAction.FileRevisionProvider;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.openapi.vcs.impl.ChangesBrowserToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CompareWithLocalDialog {
  public static void showChanges(@NotNull Project project,
                                 @NotNull @NlsContexts.DialogTitle String dialogTitle,
                                 @NotNull LocalContent localContent,
                                 @NotNull ThrowableComputable<? extends Collection<Change>, ? extends VcsException> changesLoader) {
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

    SimpleChangesBrowser changesBrowser = changesPanel.getChangesBrowser();
    DiffPreview diffPreview = ChangesBrowserToolWindow.createDiffPreview(project, changesBrowser, changesPanel);
    changesBrowser.setShowDiffActionPreview(diffPreview);

    Content content = ContentFactory.SERVICE.getInstance().createContent(changesPanel, dialogTitle, false);
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

  private static abstract class MyLoadingChangesPanel extends JPanel implements DataProvider, Disposable {
    public static final DataKey<MyLoadingChangesPanel> DATA_KEY = DataKey.create("git4idea.log.MyLoadingChangesPanel");

    private final SimpleChangesBrowser myChangesBrowser;
    private final LoadingChangesPanel myLoadingPanel;

    private MyLoadingChangesPanel(@NotNull SimpleChangesBrowser changesBrowser) {
      super(new BorderLayout());

      myChangesBrowser = changesBrowser;

      StatusText emptyText = myChangesBrowser.getViewer().getEmptyText();
      myLoadingPanel = new LoadingChangesPanel(myChangesBrowser, emptyText, this);
      add(myLoadingPanel, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {
    }

    @NotNull
    public SimpleChangesBrowser getChangesBrowser() {
      return myChangesBrowser;
    }

    public void reloadChanges() {
      myLoadingPanel.loadChangesInBackground(this::loadChanges, this::applyResult);
    }

    @NotNull
    protected abstract Collection<Change> loadChanges() throws VcsException;

    private void applyResult(@Nullable Collection<Change> changes) {
      myChangesBrowser.setChangesToDisplay(changes != null ? changes : Collections.emptyList());
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (DATA_KEY.is(dataId)) {
        return this;
      }
      return null;
    }
  }

  private static class MyChangesBrowser extends SimpleChangesBrowser implements Disposable {
    @NotNull private final CompareWithLocalDialog.LocalContent myLocalContent;

    private MyChangesBrowser(@NotNull Project project, @NotNull LocalContent localContent) {
      super(project, false, true);
      myLocalContent = localContent;

      hideViewerBorder();
    }

    @Override
    public void dispose() {
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      return ContainerUtil.append(
        super.createToolbarActions(),
        new MyGetVersionAction()
      );
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        new MyGetVersionAction()
      );
    }
  }

  private static class MyGetVersionAction extends DumbAwareAction {
    private MyGetVersionAction() {
      super(VcsBundle.messagePointer("action.name.get.file.content.from.repository"),
            VcsBundle.messagePointer("action.description.get.file.content.from.repository"), AllIcons.Actions.Download);
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
      MyLoadingChangesPanel changesPanel = e.getRequiredData(MyLoadingChangesPanel.DATA_KEY);
      MyChangesBrowser browser = (MyChangesBrowser)changesPanel.getChangesBrowser();

      List<FileRevisionProvider> fileContentProviders = ContainerUtil.map(changesPanel.getChangesBrowser().getSelectedChanges(), change -> {
        return new MyFileContentProvider(change, browser.myLocalContent);
      });
      GetVersionAction.doGet(project, VcsBundle.message("compare.with.dialog.get.from.vcs.action.title"), fileContentProviders,
                             () -> changesPanel.reloadChanges());
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
      public byte @Nullable [] getContent() throws VcsException {
        ContentRevision revision = myLocalContent == LocalContent.AFTER ? myChange.getBeforeRevision()
                                                                        : myChange.getAfterRevision();
        if (revision == null) return null;

        return ChangesUtil.loadContentRevision(revision);
      }
    }
  }

  public enum LocalContent {BEFORE, AFTER, NONE}
}
