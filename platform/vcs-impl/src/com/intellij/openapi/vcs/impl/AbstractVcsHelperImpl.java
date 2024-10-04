// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AnnotateToggleAction;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.ChangesBrowserDialog;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTableModel;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationCommittedChangesPanel;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.history.FileHistoryRefresher;
import com.intellij.openapi.vcs.history.FileHistoryRefresherI;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
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
import com.intellij.util.ui.MessageCategory;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.util.ui.ConfirmationDialog.requestForConfirmation;
import static java.text.MessageFormat.format;

public class AbstractVcsHelperImpl extends AbstractVcsHelper {
  private static final Logger LOG = Logger.getInstance(AbstractVcsHelperImpl.class);

  private Consumer<VcsException> myCustomHandler = null;

  protected AbstractVcsHelperImpl(@NotNull Project project) {
    super(project);
  }

  @ApiStatus.Internal
  public void openMessagesView(final VcsErrorViewPanel errorTreeView, @NotNull @NlsContexts.TabTitle String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      final MessageView messageView = MessageView.getInstance(myProject);
      messageView.runWhenInitialized(() -> {
        final Content content =
          ContentFactory.getInstance().createContent(errorTreeView, tabDisplayName, true);
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
      String filePrompt = format(singleFilePromptTemplate, FileUtil.getLocationRelativeToUserHome(files.get(0).getPresentableUrl()));
      if (requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, getQuestionIcon(),
                                 okActionName, cancelActionName)) {
        return new ArrayList<>(files);
      }
      return null;
    }

    SelectFilesDialog dlg = SelectFilesDialog.init(myProject, files, prompt, confirmationOption, true, false,
                                                   okActionName, cancelActionName);
    dlg.setTitle(title);
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
    return CommitChangeListDialog.commitVcsChanges(myProject, changes, initialChangeList, commitMessage, customResultHandler);
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
    return ArrayUtilRt.toStringArray(list);
  }

  private void showErrorsImpl(final boolean isEmpty,
                              final Supplier<? extends VcsException> firstGetter,
                              @NotNull @NlsContexts.TabTitle String tabDisplayName,
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
  public void showErrors(final Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull String tabDisplayName) {
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
      textFileEditor = ContainerUtil.findInstance(FileEditorManager.getInstance(myProject).getEditorList(file), TextEditor.class);
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

  private ChangesBrowserDialog createChangesBrowserDialog(CommittedChangesTableModel changelists,
                                                          @Nullable @NlsContexts.DialogTitle String title,
                                                          @Nullable Component parent,
                                                          Consumer<? super ChangesBrowserDialog> initRunnable) {
    final ChangesBrowserDialog.Mode mode = ChangesBrowserDialog.Mode.Browse;
    final ChangesBrowserDialog dlg = parent != null
                                     ? new ChangesBrowserDialog(myProject, parent, changelists, mode, initRunnable)
                                     : new ChangesBrowserDialog(myProject, changelists, mode, initRunnable);
    if (title != null) {
      dlg.setTitle(title);
    }
    return dlg;
  }

  @Override
  public void showChangesListBrowser(@NotNull CommittedChangeList changelist, @Nullable String title) {
    LoadingCommittedChangeListPanel panel = new LoadingCommittedChangeListPanel(myProject);
    panel.setChangeList(changelist, null);
    ChangeListViewerDialog.show(myProject, title, panel);
  }

  @Override
  public void showWhatDiffersBrowser(@NotNull Collection<Change> changes, @Nullable String title) {
    LoadingCommittedChangeListPanel panel = new LoadingCommittedChangeListPanel(myProject);
    panel.setChanges(changes, null);
    ChangeListViewerDialog.show(myProject, title, panel);
  }

  @Override
  @NotNull
  public List<VirtualFile> showMergeDialog(@NotNull List<? extends VirtualFile> files,
                                           @NotNull MergeProvider provider,
                                           @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    if (files.isEmpty()) return Collections.emptyList();
    RefreshVFsSynchronously.refreshVirtualFiles(files);
    final MultipleFileMergeDialog fileMergeDialog = new MultipleFileMergeDialog(myProject, files, provider, mergeDialogCustomizer);
    AppIcon.getInstance().requestAttention(myProject, true);
    fileMergeDialog.show();
    return fileMergeDialog.getProcessedFiles();
  }

  @Override
  public void showCommittedChangesBrowser(@NotNull CommittedChangesProvider provider,
                                          @NotNull RepositoryLocation location,
                                          @Nullable String title,
                                          @Nullable Component parent) {
    ChangesBrowserSettingsEditor filterUI = provider.createFilterUI(true);
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, filterUI, provider.createDefaultSettings());

    if (filterDialog.showAndGet()) {
      ChangeBrowserSettings settings = filterDialog.getSettings();

      if (myProject.isDefault() || (ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length == 0) ||
          (!ModalityState.nonModal().equals(ModalityState.current()))) {
        final List<CommittedChangeList> versions = new ArrayList<>();

        if (parent == null || !parent.isValid()) {
          parent = WindowManager.getInstance().suggestParentWindow(myProject);
        }
        final CommittedChangesTableModel model = new CommittedChangesTableModel(versions, true);
        final AsynchronousListsLoader[] task = new AsynchronousListsLoader[1];
        final ChangesBrowserDialog dlg = createChangesBrowserDialog(model, title, parent, changesBrowserDialog -> {
          task[0] = new AsynchronousListsLoader(myProject, provider, location, settings, changesBrowserDialog);
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
  public void openCommittedChangesTab(@NotNull CommittedChangesProvider provider,
                                      @NotNull RepositoryLocation location,
                                      @NotNull ChangeBrowserSettings settings,
                                      int maxCount,
                                      @Nullable String title) {
    DefaultActionGroup extraActions = new DefaultActionGroup();
    //noinspection unchecked
    RepositoryLocationCommittedChangesPanel<ChangeBrowserSettings> panel =
      new RepositoryLocationCommittedChangesPanel<>(myProject, provider, location, extraActions);
    panel.setMaxCount(maxCount);
    panel.setSettings(settings);
    panel.refreshChanges();
    final ContentFactory factory = ContentFactory.getInstance();
    if (title == null) {
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
    final var provider = vcs.getCommittedChangesProvider();
    if (provider == null) return;
    if (isNonLocal && provider.getForNonLocal(virtualFile) == null) return;

    FilePath filePath = VcsUtil.getFilePath(virtualFile);
    loadAndShowCommittedChangesDetails(project, revision, filePath,
                                       () -> getAffectedChanges(provider, virtualFile, revision, location, isNonLocal));
  }

  public static void loadAndShowCommittedChangesDetails(@NotNull Project project,
                                                        @NotNull VcsRevisionNumber revision,
                                                        @NotNull FilePath filePath,
                                                        @NotNull CommittedChangeListProvider changelistProvider) {
    loadAndShowCommittedChangesDetails(project, revision, filePath, showCommittedChangesAsTab(), changelistProvider);
  }

  public static void loadAndShowCommittedChangesDetails(@NotNull Project project,
                                                        @NotNull VcsRevisionNumber revision,
                                                        @NotNull FilePath filePath,
                                                        boolean showAsTab,
                                                        @NotNull CommittedChangeListProvider changelistProvider) {
    final String title = VcsBundle.message("paths.affected.in.revision", VcsUtil.getShortRevisionString(revision));
    final BackgroundableActionLock lock = BackgroundableActionLock.getLock(project, VcsBackgroundableActions.COMMITTED_CHANGES_DETAILS,
                                                                           revision, filePath.getPath());

    if (ChangeListViewerDialog.tryFocusExistingDialog(lock)) return;

    LoadingCommittedChangeListPanel loadingPanel = new LoadingCommittedChangeListPanel(project);
    loadingPanel.loadChangesInBackground(() -> loadCommittedChanges(revision, filePath, changelistProvider));
    ChangeListViewerDialog.show(project, title, loadingPanel, lock, showAsTab);
  }

  public static boolean showCommittedChangesAsTab() {
    return Registry.is("vcs.show.affected.files.as.tab") &&
           ModalityState.current() == ModalityState.nonModal();
  }

  @NotNull
  private static LoadingCommittedChangeListPanel.ChangelistData loadCommittedChanges(
    @NotNull VcsRevisionNumber revision,
    @NotNull FilePath filePath,
    @NotNull CommittedChangeListProvider changelistProvider) throws VcsException {
    try {
      Pair<? extends CommittedChangeList, FilePath> pair = changelistProvider.loadChangelist();
      if (pair.getFirst() == null) throw new VcsException(failedText(filePath, revision));

      CommittedChangeList changeList = pair.getFirst();
      FilePath targetPath = pair.getSecond();

      FilePath navigateToPath = ObjectUtils.notNull(targetPath, filePath);
      return new LoadingCommittedChangeListPanel.ChangelistData(changeList, navigateToPath);
    }
    catch (VcsException e) {
      throw new VcsException(failedText(filePath, revision), e);
    }
  }

  @NotNull
  private static Pair<CommittedChangeList, FilePath> getAffectedChanges(@NotNull CommittedChangesProvider provider,
                                                                        @NotNull VirtualFile virtualFile,
                                                                        @NotNull VcsRevisionNumber revision,
                                                                        @Nullable RepositoryLocation location,
                                                                        boolean isNonLocal) throws VcsException {
    if (!isNonLocal) {
      //noinspection unchecked
      Pair<CommittedChangeList, FilePath> pair = provider.getOneList(virtualFile, revision);
      if (pair != null) return pair;

      throw new VcsException(VcsBundle.message("error.cant.load.affected.files", virtualFile.getPath(), revision.asString()));
    }
    else {
      if (location != null) {
        final ChangeBrowserSettings settings = provider.createDefaultSettings();
        settings.USE_CHANGE_BEFORE_FILTER = true;
        settings.CHANGE_BEFORE = revision.asString();

        //noinspection unchecked
        final List<CommittedChangeList> changes = provider.getCommittedChanges(settings, location, 1);
        if (changes.size() == 1) {
          return Pair.create(changes.get(0), null);
        }
        else {
          throw new VcsException(VcsBundle.message("error.cant.load.affected.files", virtualFile.getPath(), revision.asString()));
        }
      }
      else {
        CommittedChangeList list = getRemoteList(provider, revision, virtualFile);
        return Pair.create(list, null);
      }
    }
  }

  @NotNull
  public static CommittedChangeList getRemoteList(@NotNull CommittedChangesProvider provider,
                                                  @NotNull VcsRevisionNumber revision,
                                                  @NotNull VirtualFile nonLocal) throws VcsException {
    final RepositoryLocation location = provider.getForNonLocal(nonLocal);
    if (location == null) {
      throw new VcsException(VcsBundle.message("error.cant.get.local.file.for.non.local", nonLocal));
    }

    final String number = revision.asString();
    final ChangeBrowserSettings settings = provider.createDefaultSettings();
    //noinspection unchecked
    final List<CommittedChangeList> changes = provider.getCommittedChanges(settings, location, provider.getUnlimitedCountValue());
    for (CommittedChangeList change : changes) {
      if (number.equals(String.valueOf(change.getNumber()))) {
        return change;
      }
    }
    LOG.warn(String.format("Cannot load affected files for location '%s' in revision '%s' with limit %s (found %s)",
                           location, revision.asString(), provider.getUnlimitedCountValue(), changes.size()), new Throwable());
    throw new VcsException(VcsBundle.message("error.cant.load.affected.files", nonLocal.getPath(), revision.asString()));
  }

  @NotNull
  private static @Nls String failedText(@NotNull FilePath filePath, @NotNull VcsRevisionNumber revision) {
    return VcsBundle.message("impl.show.all.affected.files.for.path.at.revision.failed", filePath.getPath(), revision.asString());
  }

  private static final class AsynchronousListsLoader extends Task.Backgroundable {
    @NotNull private final CommittedChangesProvider myProvider;
    @NotNull private final RepositoryLocation myLocation;
    private final ChangeBrowserSettings mySettings;
    private final ChangesBrowserDialog myDlg;
    private final List<VcsException> myExceptions;
    private volatile boolean myCanceled;
    private boolean myRevisionsReturned;

    private AsynchronousListsLoader(@Nullable Project project,
                                    @NotNull CommittedChangesProvider provider,
                                    @NotNull RepositoryLocation location,
                                    @NotNull ChangeBrowserSettings settings,
                                    final ChangesBrowserDialog dlg) {
      super(project, VcsBundle.message("browse.changes.progress.title"), true);
      myProvider = provider;
      myLocation = location;
      mySettings = settings;
      myDlg = dlg;
      myExceptions = new ArrayList<>();
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
    @NotNull
    Pair<? extends CommittedChangeList, FilePath> loadChangelist() throws VcsException;
  }
}
