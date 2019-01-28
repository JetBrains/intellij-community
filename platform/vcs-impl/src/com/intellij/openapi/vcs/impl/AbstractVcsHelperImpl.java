// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.CommonBundle;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerUtil;
import com.intellij.ui.content.MessageView;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ConfirmationDialog;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.util.ui.ConfirmationDialog.requestForConfirmation;
import static java.text.MessageFormat.format;

public class AbstractVcsHelperImpl extends AbstractVcsHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl");
  private static final String CHANGES_DETAILS_WINDOW_KEY = "CommittedChangesDetailsLock";

  private Consumer<VcsException> myCustomHandler = null;

  protected AbstractVcsHelperImpl(@NotNull Project project) {
    super(project);
  }

  public void openMessagesView(final VcsErrorViewPanel errorTreeView, @NotNull String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      final MessageView messageView = MessageView.SERVICE.getInstance(myProject);
      messageView.runWhenInitialized(() -> {
        final Content content =
          ContentFactory.SERVICE.getInstance().createContent(errorTreeView, tabDisplayName, true);
        messageView.getContentManager().addContent(content);
        Disposer.register(content, errorTreeView);
        messageView.getContentManager().setSelectedContent(content);
        ContentManagerUtil.cleanupContents(content, myProject, tabDisplayName);

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      });
    }, VcsBundle.message("command.name.open.error.message.view"), null);
  }

  @Override
  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider, @NotNull FilePath path, @NotNull AbstractVcs vcs) {
    showFileHistory(historyProvider, vcs.getAnnotationProvider(), path, vcs);
  }

  @Override
  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                              @Nullable AnnotationProvider annotationProvider,
                              @NotNull FilePath path,
                              @NotNull AbstractVcs vcs) {
    FileHistoryRefresherI refresher = FileHistoryRefresher.findOrCreate(historyProvider, path, vcs);
    refresher.selectContent();
    refresher.refresh(true);
  }

  public void showFileHistory(@NotNull VcsHistoryProviderEx historyProvider,
                              @NotNull FilePath path,
                              @NotNull AbstractVcs vcs,
                              @Nullable VcsRevisionNumber startingRevisionNumber) {
    FileHistoryRefresherI refresher = FileHistoryRefresher.findOrCreate(historyProvider, path, vcs, startingRevisionNumber);
    refresher.selectContent();
    refresher.refresh(true);
  }

  @Override
  public void showRollbackChangesDialog(List<? extends Change> changes) {
    RollbackChangesDialog.rollbackChanges(myProject, changes);
  }

  @Override
  @Nullable
  public Collection<VirtualFile> selectFilesToProcess(List<? extends VirtualFile> files,
                                                      String title,
                                                      @Nullable String prompt,
                                                      @Nullable String singleFileTitle,
                                                      @Nullable String singleFilePromptTemplate,
                                                      @NotNull VcsShowConfirmationOption confirmationOption) {
    if (files == null || files.isEmpty()) {
      return null;
    }

    final String okActionName = CommonBundle.getAddButtonText();
    final String cancelActionName = CommonBundle.getCancelButtonText();

    if (files.size() == 1 && singleFileTitle != null && singleFilePromptTemplate != null) {
      String filePrompt = MessageFormat.format(singleFilePromptTemplate,
                                               FileUtil.getLocationRelativeToUserHome(files.get(0).getPresentableUrl()));
      if (ConfirmationDialog
        .requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, Messages.getQuestionIcon(),
                                okActionName, cancelActionName)) {
        return new ArrayList<>(files);
      }
      return null;
    }

    SelectFilesDialog dlg = SelectFilesDialog.init(myProject, files, prompt, confirmationOption, true, false,
                                                   okActionName, cancelActionName);
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

  @Override
  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(@NotNull List<? extends FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       @Nullable String singleFileTitle,
                                                       @Nullable String singleFilePromptTemplate,
                                                       @NotNull VcsShowConfirmationOption confirmationOption,
                                                       @Nullable String okActionName,
                                                       @Nullable String cancelActionName) {
    if (files.size() == 1 && singleFileTitle != null && singleFilePromptTemplate != null) {
      final String filePrompt = format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle,
                                 getQuestionIcon(), okActionName, cancelActionName)) {
        return new ArrayList<>(files);
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

  @Override
  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(@NotNull List<? extends FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       @Nullable String singleFileTitle,
                                                       @Nullable String singleFilePromptTemplate,
                                                       @NotNull VcsShowConfirmationOption confirmationOption) {
    return selectFilePathsToProcess(files, title, prompt, singleFileTitle, singleFilePromptTemplate, confirmationOption, null, null);
  }

  @Override
  public void showErrors(final List<? extends VcsException> abstractVcsExceptions, @NotNull final String tabDisplayName) {
    showErrorsImpl(abstractVcsExceptions.isEmpty(), () -> abstractVcsExceptions.get(0), tabDisplayName,
                   vcsErrorViewPanel -> addDirectMessages(vcsErrorViewPanel, abstractVcsExceptions));
  }

  @Override
  public boolean commitChanges(@NotNull Collection<? extends Change> changes, @NotNull LocalChangeList initialChangeList,
                               @NotNull String commitMessage, @Nullable CommitResultHandler customResultHandler) {
    return CommitChangeListDialog.commitChanges(myProject, changes, initialChangeList,
                                                CommitChangeListDialog.collectExecutors(myProject, changes), true, commitMessage,
                                                customResultHandler);
  }

  private static void addDirectMessages(VcsErrorViewPanel vcsErrorViewPanel, List<? extends VcsException> abstractVcsExceptions) {
    for (final VcsException exception : abstractVcsExceptions) {
      String[] messages = getExceptionMessages(exception);
      vcsErrorViewPanel.addMessage(getErrorCategory(exception), messages, exception.getVirtualFile(), -1, -1, null);
    }
  }

  private static String[] getExceptionMessages(@NotNull VcsException exception) {
    String[] messages = exception.getMessages();
    if (messages.length == 0) messages = new String[]{VcsBundle.message("exception.text.unknown.error")};
    final List<String> list = new ArrayList<>();
    for (String message : messages) {
      list.addAll(StringUtil.split(StringUtil.convertLineSeparators(message), "\n"));
    }
    return ArrayUtil.toStringArray(list);
  }

  private void showErrorsImpl(final boolean isEmpty, final Getter<? extends VcsException> firstGetter, @NotNull final String tabDisplayName,
                              final Consumer<? super VcsErrorViewPanel> viewFiller) {
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
        ContentManagerUtil.cleanupContents(null, myProject, tabDisplayName);
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
        }
        else {
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

  @Override
  public List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters) {
    List<VcsException> exceptions = new ArrayList<>();

    TransactionProvider transactionProvider = vcs.getTransactionProvider();
    boolean transactionSupported = transactionProvider != null;

    if (transactionSupported) {
      transactionProvider.startTransaction(vcsParameters);
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

  @Override
  public void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs) {
    showAnnotation(annotation, file, vcs, 0);
  }

  @Override
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

    AnnotateToggleAction.doAnnotate(editor, myProject, annotation, vcs);
  }

  @Override
  public void showChangesBrowser(List<CommittedChangeList> changelists) {
    showChangesBrowser(changelists, null);
  }

  @Override
  public void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title) {
    showChangesBrowser(new CommittedChangesTableModel(changelists, false), title, false, null);
  }

  private ChangesBrowserDialog createChangesBrowserDialog(CommittedChangesTableModel changelists,
                                                          String title,
                                                          boolean showSearchAgain,
                                                          @Nullable final Component parent, Consumer<? super ChangesBrowserDialog> initRunnable) {
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

  @Override
  public void showChangesListBrowser(CommittedChangeList changelist, @Nls String title) {
    showChangesListBrowser(changelist, null, title);
  }

  @Override
  public void showWhatDiffersBrowser(final Component parent, final Collection<Change> changes, @Nls final String title) {
    final ChangeListViewerDialog dlg = new ChangeListViewerDialog(parent, myProject, changes);
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  @Override
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
          (!ModalityState.NON_MODAL.equals(ModalityState.current()))) {
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
          if (!exceptions.isEmpty()) {
            Messages.showErrorDialog(myProject, VcsBundle.message("browse.changes.error.message", exceptions.get(0).getMessage()),
                                     VcsBundle.message("browse.changes.error.title"));
            return;
          }

          if (!task[0].isRevisionsReturned()) {
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
  public List<VirtualFile> showMergeDialog(@NotNull List<? extends VirtualFile> files,
                                           @NotNull MergeProvider provider,
                                           @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    if (files.isEmpty()) return Collections.emptyList();
    VfsUtil.markDirtyAndRefresh(false, false, false, ArrayUtil.toObjectArray(files, VirtualFile.class));
    final MultipleFileMergeDialog fileMergeDialog = new MultipleFileMergeDialog(myProject, files, provider, mergeDialogCustomizer);
    AppIcon.getInstance().requestAttention(myProject, true);
    fileMergeDialog.show();
    return fileMergeDialog.getProcessedFiles();
  }

  @Override
  public void openCommittedChangesTab(final AbstractVcs vcs,
                                      final VirtualFile root,
                                      final ChangeBrowserSettings settings,
                                      final int maxCount,
                                      String title) {
    RepositoryLocationCache cache = CommittedChangesCache.getInstance(myProject).getLocationCache();
    RepositoryLocation location = cache.getLocation(vcs, VcsUtil.getFilePath(root), false);
    openCommittedChangesTab(vcs.getCommittedChangesProvider(), location, settings, maxCount, title);
  }

  @Override
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
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
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

    FilePath filePath = VcsUtil.getFilePath(virtualFile);
    loadAndShowCommittedChangesDetails(project, revision, filePath, () -> {
      return getAffectedChanges(provider, virtualFile, revision, location, isNonLocal);
    });
  }

  public static void loadAndShowCommittedChangesDetails(@NotNull Project project,
                                                        @NotNull VcsRevisionNumber revision,
                                                        @NotNull FilePath filePath,
                                                        @NotNull CommittedChangeListProvider changelistProvider) {
    final String title = VcsBundle.message("paths.affected.in.revision", VcsUtil.getShortRevisionString(revision));
    final BackgroundableActionLock lock = BackgroundableActionLock.getLock(project, VcsBackgroundableActions.COMMITTED_CHANGES_DETAILS,
                                                                           revision, filePath.getPath());

    if (lock.isLocked()) {
      for (Window window : Window.getWindows()) {
        Object windowLock = UIUtil.getWindowClientProperty(window, CHANGES_DETAILS_WINDOW_KEY);
        if (windowLock != null && lock.equals(windowLock)) {
          UIUtil.toFront(window);
          break;
        }
      }
      return;
    }
    lock.lock();

    ChangeListViewerDialog dlg = new ChangeListViewerDialog(project);
    dlg.setTitle(title);

    UIUtil.putWindowClientProperty(dlg.getWindow(), CHANGES_DETAILS_WINDOW_KEY, lock);
    Disposer.register(dlg.getDisposable(), () -> lock.unlock());

    dlg.loadChangesInBackground(() -> {
      try {
        Pair<? extends CommittedChangeList, FilePath> pair = changelistProvider.loadChangelist();
        if (pair == null || pair.getFirst() == null) throw new VcsException(failedText(filePath, revision));

        CommittedChangeList changeList = pair.getFirst();
        FilePath targetPath = pair.getSecond();

        FilePath navigateToPath = ObjectUtils.notNull(targetPath, filePath);
        return new ChangeListViewerDialog.ChangelistData(changeList, navigateToPath);
      }
      catch (VcsException e) {
        throw new VcsException(failedText(filePath, revision), e);
      }
    });
    dlg.show();
  }

  @Nullable
  private static Pair<CommittedChangeList, FilePath> getAffectedChanges(@NotNull CommittedChangesProvider provider,
                                                                        @NotNull VirtualFile virtualFile,
                                                                        @NotNull VcsRevisionNumber revision,
                                                                        @Nullable RepositoryLocation location,
                                                                        boolean isNonLocal) throws VcsException {
    if (!isNonLocal) {
      //noinspection unchecked
      Pair<CommittedChangeList, FilePath> pair = provider.getOneList(virtualFile, revision);
      if (pair != null) return pair;
    }
    else {
      if (location != null) {
        final ChangeBrowserSettings settings = provider.createDefaultSettings();
        settings.USE_CHANGE_BEFORE_FILTER = true;
        settings.CHANGE_BEFORE = revision.asString();

        //noinspection unchecked
        final List<CommittedChangeList> changes = provider.getCommittedChanges(settings, location, 1);
        if (changes != null && changes.size() == 1) {
          return Pair.create(changes.get(0), null);
        }
      }
      else {
        CommittedChangeList list = getRemoteList(provider, revision, virtualFile);
        if (list != null) return Pair.create(list, null);
      }
    }
    LOG.warn(String.format("Can't get affected files: path: %s; revision: %s; location: %s; nonLocal: %s",
                           virtualFile.getPath(), revision.asString(), location, isNonLocal), new Throwable());
    return null;
  }

  @Nullable
  public static CommittedChangeList getRemoteList(@NotNull CommittedChangesProvider provider,
                                                  @NotNull VcsRevisionNumber revision,
                                                  @NotNull VirtualFile nonLocal) throws VcsException {
    final RepositoryLocation local = provider.getForNonLocal(nonLocal);
    if (local != null) {
      final String number = revision.asString();
      final ChangeBrowserSettings settings = provider.createDefaultSettings();
      //noinspection unchecked
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
  private static String failedText(@NotNull FilePath filePath, @NotNull VcsRevisionNumber revision) {
    return "Show all affected files for " + filePath.getPath() + " at " + revision.asString() + " failed";
  }

  private static class AsynchronousListsLoader extends Task.Backgroundable {
    private final CommittedChangesProvider myProvider;
    private final RepositoryLocation myLocation;
    private final ChangeBrowserSettings mySettings;
    private final ChangesBrowserDialog myDlg;
    private final List<VcsException> myExceptions;
    private volatile boolean myCanceled;
    private boolean myRevisionsReturned;

    private AsynchronousListsLoader(@Nullable Project project,
                                    final CommittedChangesProvider provider,
                                    final RepositoryLocation location,
                                    final ChangeBrowserSettings settings,
                                    final ChangesBrowserDialog dlg) {
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

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      final AsynchConsumer<List<CommittedChangeList>> appender = myDlg.getAppender();
      final BufferedListConsumer<CommittedChangeList> bufferedListConsumer = new BufferedListConsumer<>(10, appender, -1);

      final Application application = ApplicationManager.getApplication();
      try {
        myProvider.loadCommittedChanges(mySettings, myLocation, 0, new AsynchConsumer<CommittedChangeList>() {
          @Override
          public void consume(CommittedChangeList committedChangeList) {
            myRevisionsReturned = true;
            bufferedListConsumer.consumeOne(committedChangeList);
            if (myCanceled) {
              indicator.cancel();
            }
          }

          @Override
          public void finished() {
            bufferedListConsumer.flush();
            appender.finished();

            if (!myRevisionsReturned) {
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

  public interface CommittedChangeListProvider {
    @Nullable
    Pair<? extends CommittedChangeList, FilePath> loadChangelist() throws VcsException;
  }
}
