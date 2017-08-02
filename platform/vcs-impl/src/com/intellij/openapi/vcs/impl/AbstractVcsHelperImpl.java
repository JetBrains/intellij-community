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
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.ide.errorTreeView.SimpleErrorData;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AnnotateToggleAction;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.*;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.history.FileHistoryRefresher;
import com.intellij.openapi.vcs.history.FileHistoryRefresherI;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ConfirmationDialog;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.util.ui.ConfirmationDialog.requestForConfirmation;
import static java.text.MessageFormat.format;

public class AbstractVcsHelperImpl extends AbstractVcsHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl");

  private Consumer<VcsException> myCustomHandler = null;

  protected AbstractVcsHelperImpl(@NotNull Project project) {
    super(project);
  }

  public void openMessagesView(final VcsErrorViewPanel errorTreeView, final String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      final MessageView messageView = MessageView.SERVICE.getInstance(myProject);
      messageView.runWhenInitialized(() -> {
        final Content content =
          ContentFactory.SERVICE.getInstance().createContent(errorTreeView, tabDisplayName, true);
        messageView.getContentManager().addContent(content);
        Disposer.register(content, errorTreeView);
        messageView.getContentManager().setSelectedContent(content);
        removeContents(content, tabDisplayName);

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      });
    }, VcsBundle.message("command.name.open.error.message.view"), null);
  }

  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                              @NotNull FilePath path,
                              @NotNull AbstractVcs vcs,
                              @Nullable String repositoryPath) {
    showFileHistory(historyProvider, vcs.getAnnotationProvider(), path, repositoryPath, vcs);
  }

  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                              @Nullable AnnotationProvider annotationProvider,
                              @NotNull FilePath path,
                              @Nullable String repositoryPath,
                              @NotNull AbstractVcs vcs) {
    FileHistoryRefresherI refresher = FileHistoryRefresher.findOrCreate(historyProvider, path, vcs);
    refresher.run(false, true);
  }

  public void showFileHistory(@NotNull VcsHistoryProviderEx historyProvider,
                              @NotNull FilePath path,
                              @NotNull AbstractVcs vcs,
                              @Nullable VcsRevisionNumber startingRevisionNumber) {
    FileHistoryRefresherI refresher = FileHistoryRefresher.findOrCreate(historyProvider, path, vcs, startingRevisionNumber);
    refresher.run(false, true);
  }

  public void showRollbackChangesDialog(List<Change> changes) {
    RollbackChangesDialog.rollbackChanges(myProject, changes);
  }

  @Nullable
  public Collection<VirtualFile> selectFilesToProcess(final List<VirtualFile> files,
                                                      final String title,
                                                      @Nullable final String prompt,
                                                      final String singleFileTitle,
                                                      final String singleFilePromptTemplate,
                                                      final VcsShowConfirmationOption confirmationOption) {
    if (files == null || files.isEmpty()) {
      return null;
    }

    if (files.size() == 1 && singleFilePromptTemplate != null) {
      String filePrompt = MessageFormat.format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (ConfirmationDialog
        .requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, Messages.getQuestionIcon())) {
        return files;
      }
      return null;
    }

    SelectFilesDialog dlg = SelectFilesDialog.init(myProject, files, prompt, confirmationOption, true, true, false);
    dlg.setTitle(title);
    if (!confirmationOption.isPersistent()) {
      dlg.setDoNotAskOption(null);
    }
    if (dlg.showAndGet()) {
      final Collection<VirtualFile> selection = dlg.getSelectedFiles();
      // return items in the same order as they were passed to us
      final List<VirtualFile> result = new ArrayList<>();
      for (VirtualFile file : files) {
        if (selection.contains(file)) {
          result.add(file);
        }
      }
      return result;
    }
    return null;
  }

  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(List<FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       String singleFileTitle,
                                                       String singleFilePromptTemplate,
                                                       VcsShowConfirmationOption confirmationOption,
                                                       @Nullable String okActionName,
                                                       @Nullable String cancelActionName) {
    if (files.size() == 1 && singleFilePromptTemplate != null) {
      final String filePrompt = format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle,
                                 getQuestionIcon(), okActionName, cancelActionName)) {
        return files;
      }
      return null;
    }

    final SelectFilePathsDialog dlg =
      new SelectFilePathsDialog(myProject, files, prompt, confirmationOption, okActionName, cancelActionName, true);
    dlg.setTitle(title);
    if (!confirmationOption.isPersistent()) {
      dlg.setDoNotAskOption(null);
    }
    return dlg.showAndGet() ? dlg.getSelectedFiles() : null;
  }

  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(final List<FilePath> files,
                                                       final String title,
                                                       @Nullable final String prompt,
                                                       final String singleFileTitle,
                                                       final String singleFilePromptTemplate,
                                                       final VcsShowConfirmationOption confirmationOption) {
    return selectFilePathsToProcess(files, title, prompt, singleFileTitle, singleFilePromptTemplate, confirmationOption, null, null);
  }

  public void showErrors(final List<VcsException> abstractVcsExceptions, @NotNull final String tabDisplayName) {
    showErrorsImpl(abstractVcsExceptions.isEmpty(), () -> abstractVcsExceptions.get(0), tabDisplayName, vcsErrorViewPanel -> addDirectMessages(vcsErrorViewPanel, abstractVcsExceptions));
  }
  
  @Override
  public boolean commitChanges(@NotNull Collection<Change> changes, @NotNull LocalChangeList initialChangeList,
                               @NotNull String commitMessage, @Nullable CommitResultHandler customResultHandler) {
      return CommitChangeListDialog.commitChanges(myProject, changes, initialChangeList,
                                                  CommitChangeListDialog.collectExecutors(myProject, changes), true, commitMessage,
                                                  customResultHandler);
  }

  private static void addDirectMessages(VcsErrorViewPanel vcsErrorViewPanel, List<VcsException> abstractVcsExceptions) {
    for (final VcsException exception : abstractVcsExceptions) {
      String[] messages = getExceptionMessages(exception);
      vcsErrorViewPanel.addMessage(getErrorCategory(exception), messages, exception.getVirtualFile(), -1, -1, null);
    }
  }

  private static String[] getExceptionMessages(VcsException exception) {
    String[] messages = exception.getMessages();
    if (messages.length == 0) messages = new String[]{VcsBundle.message("exception.text.unknown.error")};
    final List<String> list = new ArrayList<>();
    for (String message : messages) {
      list.addAll(StringUtil.split(StringUtil.convertLineSeparators(message), "\n"));
    }
    return ArrayUtil.toStringArray(list);
  }

  private void showErrorsImpl(final boolean isEmpty, final Getter<VcsException> firstGetter, @NotNull final String tabDisplayName,
                              final Consumer<VcsErrorViewPanel> viewFiller) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!isEmpty) {
        VcsException exception = firstGetter.get();
        if (!handleCustom(exception)) {
          throw new RuntimeException(exception);
        }
      }
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (isEmpty) {
        removeContents(null, tabDisplayName);
        return;
      }

      final VcsErrorViewPanel errorTreeView = new VcsErrorViewPanel(myProject);
      openMessagesView(errorTreeView, tabDisplayName);

      viewFiller.consume(errorTreeView);
    });
  }

  public boolean handleCustom(VcsException exception) {
    if (myCustomHandler != null) {
      myCustomHandler.consume(exception);
      return true;
    }
    return false;
  }

  @Override
  public void showErrors(final Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull final String tabDisplayName) {
    showErrorsImpl(exceptionGroups.isEmpty(), () -> {
      final List<VcsException> exceptionList = exceptionGroups.values().iterator().next();
      return exceptionList == null ? null : (exceptionList.isEmpty() ? null : exceptionList.get(0));
    }, tabDisplayName, vcsErrorViewPanel -> {
      for (Map.Entry<HotfixData, List<VcsException>> entry : exceptionGroups.entrySet()) {
        if (entry.getKey() == null) {
          addDirectMessages(vcsErrorViewPanel, entry.getValue());
        } else {
          final List<VcsException> exceptionList = entry.getValue();
          final List<SimpleErrorData> list = new ArrayList<>(exceptionList.size());
          for (VcsException exception : exceptionList) {
            final String[] messages = getExceptionMessages(exception);
            list.add(new SimpleErrorData(
              ErrorTreeElementKind.convertMessageFromCompilerErrorType(getErrorCategory(exception)), messages, exception.getVirtualFile()));
          }

          vcsErrorViewPanel.addHotfixGroup(entry.getKey(), list);
        }
      }
    });
  }

  private static int getErrorCategory(VcsException exception) {
    if (exception.isWarning()) {
      return MessageCategory.WARNING;
    }
    else {
      return MessageCategory.ERROR;
    }
  }

  protected void removeContents(Content notToRemove, final String tabDisplayName) {
    MessageView messageView = MessageView.SERVICE.getInstance(myProject);
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      LOG.assertTrue(content != null);
      if (content.isPinned()) continue;
      if (tabDisplayName.equals(content.getDisplayName()) && content != notToRemove) {
        ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
        if (listErrorView != null) {
          if (messageView.getContentManager().removeContent(content, true)) {
            content.release();
          }
        }
      }
    }
  }

  public List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters) {
    List<VcsException> exceptions = new ArrayList<>();

    TransactionProvider transactionProvider = vcs.getTransactionProvider();
    boolean transactionSupported = transactionProvider != null;

    if (transactionSupported) {
      try {
        transactionProvider.startTransaction(vcsParameters);
      }
      catch (VcsException e) {
        return Collections.singletonList(e);
      }
    }

    runnable.run(exceptions);

    if (transactionSupported) {
      if (exceptions.isEmpty()) {
        try {
          transactionProvider.commitTransaction(vcsParameters);
        }
        catch (VcsException e) {
          exceptions.add(e);
          transactionProvider.rollbackTransaction(vcsParameters);
        }
      }
      else {
        transactionProvider.rollbackTransaction(vcsParameters);
      }
    }

    return exceptions;
  }

  public void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs) {
    showAnnotation(annotation, file, vcs, 0);
  }

  public void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs, int line) {
    TextEditor textFileEditor;
    FileEditor fileEditor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
    if (fileEditor instanceof TextEditor) {
      textFileEditor = ((TextEditor)fileEditor);
    }
    else {
      FileEditor[] editors = FileEditorManager.getInstance(myProject).getEditors(file);
      textFileEditor = ContainerUtil.findInstance(editors, TextEditor.class);
    }

    Editor editor;
    if (textFileEditor != null) {
      editor = textFileEditor.getEditor();
    }
    else {
      OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(myProject, file, line, 0);
      editor = FileEditorManager.getInstance(myProject).openTextEditor(openFileDescriptor, true);
    }

    if (editor == null) {
      Messages.showMessageDialog(VcsBundle.message("message.text.cannot.open.editor", file.getPresentableUrl()),
                                 VcsBundle.message("message.title.cannot.open.editor"), Messages.getInformationIcon());
      return;
    }

    AnnotateToggleAction.doAnnotate(editor, myProject, file, annotation, vcs);
  }

  public void showChangesBrowser(List<CommittedChangeList> changelists) {
    showChangesBrowser(changelists, null);
  }

  public void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title) {
    showChangesBrowser(new CommittedChangesTableModel(changelists, false), title, false, null);
  }

  private ChangesBrowserDialog createChangesBrowserDialog(CommittedChangesTableModel changelists,
                                                          String title,
                                                          boolean showSearchAgain,
                                                          @Nullable final Component parent, Consumer<ChangesBrowserDialog> initRunnable) {
    final ChangesBrowserDialog.Mode mode = showSearchAgain ? ChangesBrowserDialog.Mode.Browse : ChangesBrowserDialog.Mode.Simple;
    final ChangesBrowserDialog dlg = parent != null
                                     ? new ChangesBrowserDialog(myProject, parent, changelists, mode, initRunnable)
                                     : new ChangesBrowserDialog(myProject, changelists, mode, initRunnable);
    if (title != null) {
      dlg.setTitle(title);
    }
    return dlg;
  }

  private void showChangesBrowser(CommittedChangesTableModel changelists,
                                  String title,
                                  boolean showSearchAgain,
                                  @Nullable final Component parent) {
    final ChangesBrowserDialog.Mode mode = showSearchAgain ? ChangesBrowserDialog.Mode.Browse : ChangesBrowserDialog.Mode.Simple;
    final ChangesBrowserDialog dlg = parent != null
                                     ? new ChangesBrowserDialog(myProject, parent, changelists, mode, null)
                                     : new ChangesBrowserDialog(myProject, changelists, mode, null);
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  @Override
  public void showChangesListBrowser(CommittedChangeList changelist, @Nullable VirtualFile toSelect, @Nls String title) {
    final ChangeListViewerDialog dlg = new ChangeListViewerDialog(myProject, changelist, toSelect);
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  public void showChangesListBrowser(CommittedChangeList changelist, @Nls String title) {
    showChangesListBrowser(changelist, null, title);
  }

  public void showWhatDiffersBrowser(final Component parent, final Collection<Change> changes, @Nls final String title) {
    final ChangeListViewerDialog dlg;
    if (parent != null) {
      dlg = new ChangeListViewerDialog(parent, myProject, changes, false);
    }
    else {
      dlg = new ChangeListViewerDialog(myProject, changes, false);
    }
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  public void showChangesBrowser(final CommittedChangesProvider provider,
                                 final RepositoryLocation location,
                                 @Nls String title,
                                 Component parent) {
    final ChangesBrowserSettingsEditor filterUI = provider.createFilterUI(true);
    ChangeBrowserSettings settings = provider.createDefaultSettings();
    boolean ok;
    if (filterUI != null) {
      final CommittedChangesFilterDialog dlg = new CommittedChangesFilterDialog(myProject, filterUI, settings);
      dlg.show();
      ok = dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE;
      settings = dlg.getSettings();
    }
    else {
      ok = true;
    }

    if (ok) {
      if (myProject.isDefault() || (ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length == 0) ||
          (! ModalityState.NON_MODAL.equals(ModalityState.current()))) {
        final List<CommittedChangeList> versions = new ArrayList<>();

        if (parent == null || !parent.isValid()) {
          parent = WindowManager.getInstance().suggestParentWindow(myProject);
        }
        final CommittedChangesTableModel model = new CommittedChangesTableModel(versions, true);
        final AsynchronousListsLoader[] task = new AsynchronousListsLoader[1];
        final ChangeBrowserSettings finalSettings = settings;
        final ChangesBrowserDialog dlg = createChangesBrowserDialog(model, title, filterUI != null, parent, changesBrowserDialog -> {
          task[0] = new AsynchronousListsLoader(myProject, provider, location, finalSettings, changesBrowserDialog);
          ProgressManager.getInstance().run(task[0]);
        });

        dlg.startLoading();
        dlg.show();
        if (task[0] != null) {
          task[0].cancel();
          final List<VcsException> exceptions = task[0].getExceptions();
          if (! exceptions.isEmpty()) {
            Messages.showErrorDialog(myProject, VcsBundle.message("browse.changes.error.message", exceptions.get(0).getMessage()),
                                     VcsBundle.message("browse.changes.error.title"));
            return;
          }

          if (! task[0].isRevisionsReturned()) {
            Messages.showInfoMessage(myProject, VcsBundle.message("browse.changes.nothing.found"),
                                     VcsBundle.message("browse.changes.nothing.found.title"));
          }
        }
      }
      else {
        openCommittedChangesTab(provider, location, settings, 0, title);
      }
    }
  }

  @Override
  @NotNull
  public List<VirtualFile> showMergeDialog(@NotNull List<VirtualFile> files,
                                           @NotNull MergeProvider provider,
                                           @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    if (files.isEmpty()) return Collections.emptyList();
    VfsUtil.markDirtyAndRefresh(false, false, false, ArrayUtil.toObjectArray(files, VirtualFile.class));
    final MultipleFileMergeDialog fileMergeDialog = new MultipleFileMergeDialog(myProject, files, provider, mergeDialogCustomizer);
    AppIcon.getInstance().requestAttention(myProject, true);
    fileMergeDialog.show();
    return fileMergeDialog.getProcessedFiles();
  }

  public void openCommittedChangesTab(final AbstractVcs vcs,
                                      final VirtualFile root,
                                      final ChangeBrowserSettings settings,
                                      final int maxCount,
                                      String title) {
    RepositoryLocationCache cache = CommittedChangesCache.getInstance(myProject).getLocationCache();
    RepositoryLocation location = cache.getLocation(vcs, VcsUtil.getFilePath(root), false);
    openCommittedChangesTab(vcs.getCommittedChangesProvider(), location, settings, maxCount, title);
  }

  public void openCommittedChangesTab(final CommittedChangesProvider provider,
                                      final RepositoryLocation location,
                                      final ChangeBrowserSettings settings,
                                      final int maxCount,
                                      String title) {
    DefaultActionGroup extraActions = new DefaultActionGroup();
    CommittedChangesPanel panel = new CommittedChangesPanel(myProject, provider, settings, location, extraActions);
    panel.setMaxCount(maxCount);
    panel.refreshChanges(false);
    final ContentFactory factory = ContentFactory.SERVICE.getInstance();
    if (title == null && location != null) {
      title = VcsBundle.message("browse.changes.content.title", location.toPresentableString());
    }
    final Content content = factory.createContent(panel, title, false);
    final ChangesViewContentI contentManager = ChangesViewContentManager.getInstance(myProject);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    extraActions.add(new CloseTabToolbarAction() {
      public void actionPerformed(final AnActionEvent e) {
        contentManager.removeContent(content);
      }
    });

    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (!window.isVisible()) {
      window.activate(null);
    }
  }

  @Override
  public void loadAndShowCommittedChangesDetails(@NotNull final Project project,
                                                 @NotNull final VcsRevisionNumber revision,
                                                 @NotNull final VirtualFile virtualFile,
                                                 @NotNull VcsKey vcsKey,
                                                 @Nullable final RepositoryLocation location,
                                                 final boolean isNonLocal) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.getName());
    if (vcs == null) return;
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    if (provider == null) return;
    if (isNonLocal && provider.getForNonLocal(virtualFile) == null) return;

    final String title = VcsBundle.message("paths.affected.in.revision", VcsUtil.getShortRevisionString(revision));
    final CommittedChangeList[] list = new CommittedChangeList[1];
    final FilePath[] targetPath = new FilePath[1];
    final VcsException[] exc = new VcsException[1];

    final BackgroundableActionLock lock = BackgroundableActionLock.getLock(project, VcsBackgroundableActions.COMMITTED_CHANGES_DETAILS,
                                                                           revision, virtualFile.getPath());
    if (lock.isLocked()) return;
    lock.lock();

    Task.Backgroundable task = new Task.Backgroundable(project, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          if (!isNonLocal) {
            final Pair<CommittedChangeList, FilePath> pair = provider.getOneList(virtualFile, revision);
            if (pair != null) {
              list[0] = pair.getFirst();
              targetPath[0] = pair.getSecond();
            }
          }
          else {
            if (location != null) {
              final ChangeBrowserSettings settings = provider.createDefaultSettings();
              settings.USE_CHANGE_BEFORE_FILTER = true;
              settings.CHANGE_BEFORE = revision.asString();
              final List<CommittedChangeList> changes = provider.getCommittedChanges(settings, location, 1);
              if (changes != null && changes.size() == 1) {
                list[0] = changes.get(0);
              }
            }
            else {
              list[0] = getRemoteList(vcs, revision, virtualFile);
            }
          }
        }
        catch (VcsException e) {
          exc[0] = e;
        }
      }

      @Override
      public void onCancel() {
        lock.unlock();
      }

      @Override
      public void onSuccess() {
        lock.unlock();
        if (exc[0] != null) {
          showError(exc[0], failedText(virtualFile, revision));
        }
        else if (list[0] == null) {
          Messages.showErrorDialog(project, failedText(virtualFile, revision), getTitle());
        }
        else {
          VirtualFile navigateToFile = targetPath[0] != null ?
                                       new VcsVirtualFile(targetPath[0].getPath(), null, VcsFileSystem.getInstance()) :
                                       virtualFile;
          showChangesListBrowser(list[0], navigateToFile, title);
        }
      }
    };

    // we can's use runProcessWithProgressAsynchronously(task) because then ModalityState.NON_MODAL would be used
    CoreProgressManager progressManager = (CoreProgressManager)ProgressManager.getInstance();
    progressManager.runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task), null, ModalityState.current());
  }

  @Nullable
  public static CommittedChangeList getRemoteList(@NotNull AbstractVcs vcs,
                                                  @NotNull VcsRevisionNumber revision,
                                                  @NotNull VirtualFile nonLocal)
    throws VcsException {
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    final RepositoryLocation local = provider.getForNonLocal(nonLocal);
    if (local != null) {
      final String number = revision.asString();
      final ChangeBrowserSettings settings = provider.createDefaultSettings();
      final List<CommittedChangeList> changes = provider.getCommittedChanges(settings, local, provider.getUnlimitedCountValue());
      if (changes != null) {
        for (CommittedChangeList change : changes) {
          if (number.equals(String.valueOf(change.getNumber()))) {
            return change;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static String failedText(@NotNull VirtualFile virtualFile, @NotNull VcsRevisionNumber revision) {
    return "Show all affected files for " + virtualFile.getPath() + " at " + revision.asString() + " failed";
  }

  private static class AsynchronousListsLoader extends Task.Backgroundable {
    private final CommittedChangesProvider myProvider;
    private final RepositoryLocation myLocation;
    private final ChangeBrowserSettings mySettings;
    private final ChangesBrowserDialog myDlg;
    private final List<VcsException> myExceptions;
    private volatile boolean myCanceled;
    private boolean myRevisionsReturned;

    private AsynchronousListsLoader(@Nullable Project project, final CommittedChangesProvider provider,
                                    final RepositoryLocation location, final ChangeBrowserSettings settings, final ChangesBrowserDialog dlg) {
      super(project, VcsBundle.message("browse.changes.progress.title"), true);
      myProvider = provider;
      myLocation = location;
      mySettings = settings;
      myDlg = dlg;
      myExceptions = new LinkedList<>();
    }

    public void cancel() {
      myCanceled = true;
    }

    public void run(@NotNull final ProgressIndicator indicator) {
      final AsynchConsumer<List<CommittedChangeList>> appender = myDlg.getAppender();
      final BufferedListConsumer<CommittedChangeList> bufferedListConsumer = new BufferedListConsumer<>(10, appender, -1);

      final Application application = ApplicationManager.getApplication();
      try {
        myProvider.loadCommittedChanges(mySettings, myLocation, 0, new AsynchConsumer<CommittedChangeList>() {
          public void consume(CommittedChangeList committedChangeList) {
            myRevisionsReturned = true;
            bufferedListConsumer.consumeOne(committedChangeList);
            if (myCanceled) {
              indicator.cancel();
            }
          }

          public void finished() {
            bufferedListConsumer.flush();
            appender.finished();

            if (! myRevisionsReturned) {
              application.invokeLater(() -> myDlg.close(-1), ModalityState.stateForComponent(myDlg.getWindow()));
            }
          }
        });
      }
      catch (VcsException e) {
        myExceptions.add(e);
        application.invokeLater(() -> myDlg.close(-1), ModalityState.stateForComponent(myDlg.getWindow()));
      }
    }

    public List<VcsException> getExceptions() {
      return myExceptions;
    }

    public boolean isRevisionsReturned() {
      return myRevisionsReturned;
    }
  }

  @TestOnly
  public static void setCustomExceptionHandler(Project project, Consumer<VcsException> customHandler) {
    ((AbstractVcsHelperImpl)getInstance(project)).myCustomHandler = customHandler;
  }
}
