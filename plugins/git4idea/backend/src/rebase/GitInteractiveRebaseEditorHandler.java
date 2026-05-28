// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import git4idea.DialogManager;
import git4idea.commands.GitImplBase;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitLogUtil;
import git4idea.i18n.GitBundle;
import git4idea.rebase.interactive.GitRebaseTodoModel;
import git4idea.rebase.interactive.GitRewordedCommitMessageProvider;
import git4idea.rebase.interactive.RewordedCommitMessageMapping;
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.CommonBundle.getOkButtonText;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static git4idea.DialogManager.showOkCancelDialog;
import static git4idea.rebase.interactive.GitRebaseTodoModelConverterKt.convertToEntries;

/**
 * The handler for rebase editor request. The handler shows the {@link GitInteractiveRebaseDialog}
 * dialog with the specified file. If user accepts the changes, it saves file and returns 0,
 * otherwise it just returns error code.
 */
public class GitInteractiveRebaseEditorHandler implements GitRebaseEditorHandler {
  private static final Logger LOG = Logger.getInstance(GitInteractiveRebaseEditorHandler.class);
  private final Project myProject;
  private final VirtualFile myRoot;

  /**
   * If interactive rebase editor (with the list of commits) was shown, this is true.
   * In that case, the class expects only unstructured editor to edit the commit message.
   */
  protected boolean myRebaseEditorShown = false;

  private GitRebaseEditingResult myResult = null;
  private final @NotNull GitRewordedCommitMessageProvider myRewordedCommitMessageProvider;

  public GitInteractiveRebaseEditorHandler(@NotNull Project project, @NotNull VirtualFile root) {
    myProject = project;
    myRoot = root;
    myRewordedCommitMessageProvider = GitRewordedCommitMessageProvider.getInstance(project);
  }

  @Override
  public int editCommits(@NotNull File file) {
    GitRebaseEditingResult result;
    try {
      if (myRebaseEditorShown) {
        Charset encoding = GitConfigUtil.getCommitEncodingCharset(myProject, myRoot);
        String originalMessage = FileUtil.loadFile(file, encoding);
        String newMessage = myRewordedCommitMessageProvider.getRewordedCommitMessage(myProject, myRoot, originalMessage);
        if (newMessage == null) {
          boolean unstructuredEditorCancelled = !handleUnstructuredEditor(file);
          result = unstructuredEditorCancelled ?
                   GitRebaseEditingResult.UnstructuredEditorCancelled.INSTANCE :
                   GitRebaseEditingResult.WasEdited.INSTANCE;
        }
        else {
          FileUtil.writeToFile(file, newMessage.getBytes(encoding));
          result = GitRebaseEditingResult.WasEdited.INSTANCE;
        }
      }
      else {
        setRebaseEditorShown();
        boolean success = handleInteractiveEditor(file);
        result = success ? GitRebaseEditingResult.WasEdited.INSTANCE : GitRebaseEditingResult.CommitListEditorCancelled.INSTANCE;
      }
    }
    catch (VcsException e) {
      LOG.warn("Failed to load commit details for commits from git rebase file: " + file, e);
      result = new GitRebaseEditingResult.Failed(e);
    }
    catch (Exception e) {
      LOG.error("Failed to edit git rebase file: " + file, e);
      result = new GitRebaseEditingResult.Failed(e);
    }
    myResult = result;
    return result.getExitCode();
  }

  protected boolean handleUnstructuredEditor(@NotNull File file) throws IOException {
    return GitImplBase.loadFileAndShowInSimpleEditor(
      myProject,
      myRoot,
      file,
      GitBundle.message("rebase.interactive.edit.commit.message.dialog.title"),
      GitBundle.message("rebase.interactive.edit.commit.message.ok.action.title")
    );
  }

  protected boolean handleInteractiveEditor(@NotNull File file) throws IOException, VcsException {
    GitInteractiveRebaseFile rebaseFile = new GitInteractiveRebaseFile(myProject, myRoot, file);
    try {
      List<GitRebaseEntry> entries = rebaseFile.load();
      if (ContainerUtil.findInstance(ContainerUtil.map(entries, it -> it.getAction()), GitRebaseEntry.Action.Other.class) != null) {
        return handleUnstructuredEditor(file);
      }
      List<? extends GitRebaseEntry> newEntries = collectNewEntries(entries);
      if (newEntries != null) {
        rebaseFile.save(newEntries);
        return true;
      }
      else {
        rebaseFile.cancel();
        return false;
      }
    }
    catch (GitInteractiveRebaseFile.NoopException e) {
      return confirmNoopRebase();
    }
  }

  protected @Nullable List<? extends GitRebaseEntry> collectNewEntries(@NotNull List<GitRebaseEntry> entries) throws VcsException {
    Ref<List<? extends GitRebaseEntry>> newText = Ref.create();
    List<GitRebaseEntry> entriesWithDetails = loadDetailsForEntries(entries);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      newText.set(showInteractiveRebaseDialog(entriesWithDetails));
    });
    return newText.get();
  }

  private @Nullable List<? extends GitRebaseEntry> showInteractiveRebaseDialog(List<GitRebaseEntry> entries) {
    GitInteractiveRebaseDialog<GitRebaseEntry> editor = new GitInteractiveRebaseDialog<>(myProject, myRoot, entries);
    DialogManager.show(editor);
    if (editor.isOK()) {
      GitRebaseTodoModel<GitRebaseEntry> rebaseTodoModel = editor.getModel();
      processModel(rebaseTodoModel);
      return convertToEntries(rebaseTodoModel);
    }
    return null;
  }

  protected void processModel(@NotNull GitRebaseTodoModel<? extends GitRebaseEntry> rebaseTodoModel) {
    processModel(rebaseTodoModel, (entry) -> ((GitRebaseEntryWithDetails)entry).getCommitDetails().getFullMessage());
  }

  protected <T extends GitRebaseEntry> void processModel(
    @NotNull GitRebaseTodoModel<T> rebaseTodoModel,
    @NotNull Function<T, String> fullMessageGetter
  ) {
    List<RewordedCommitMessageMapping> messages = new ArrayList<>();
    for (GitRebaseTodoModel.Element<T> element : rebaseTodoModel.getElements()) {
      if (element.getType() instanceof GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword type) {
        messages.add(RewordedCommitMessageMapping.fromMapping(
          fullMessageGetter.apply(element.getEntry()),
          type.getNewMessage()
        ));
      }
    }
    myRewordedCommitMessageProvider.save(myProject, myRoot, messages);
  }

  private @NotNull List<GitRebaseEntry> loadDetailsForEntries(@NotNull List<GitRebaseEntry> entries) throws VcsException {
    List<String> commitList = entries.stream().filter(entry -> entry.getAction().isCommit()).map(GitRebaseEntry::getCommit).toList();
    List<? extends VcsCommitMetadata> details = GitLogUtil.collectMetadata(myProject, myRoot, commitList);
    List<GitRebaseEntry> entriesWithDetails = new ArrayList<>();
    int detailsIndex = 0;
    for (GitRebaseEntry entry : entries) {
      if (entry.getAction().isCommit()) {
        entriesWithDetails.add(new GitRebaseEntryWithDetails(entry, details.get(detailsIndex++)));
      }
      else {
        entriesWithDetails.add(entry);
      }
    }
    return entriesWithDetails;
  }

  private boolean confirmNoopRebase() {
    LOG.info("Noop situation while rebasing " + myRoot);
    Ref<Boolean> result = Ref.create(false);
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(
      Messages.OK == showOkCancelDialog(
        myProject,
        GitBundle.message("rebase.interactive.noop.dialog.text"),
        GitBundle.message("rebase.interactive.noop.dialog.title"),
        getOkButtonText(),
        getCancelButtonText(),
        getQuestionIcon()
      )
    ));
    return result.get();
  }

  /**
   * This method is invoked to indicate that this editor will be invoked in the rebase continuation action.
   */
  public void setRebaseEditorShown() {
    myRebaseEditorShown = true;
  }

  @Override
  public @Nullable GitRebaseEditingResult getEditingResult() {
    return myResult;
  }
}
