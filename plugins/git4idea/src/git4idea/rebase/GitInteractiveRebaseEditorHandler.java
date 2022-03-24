// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final static Logger LOG = Logger.getInstance(GitInteractiveRebaseEditorHandler.class);
  private final Project myProject;
  private final VirtualFile myRoot;

  /**
   * If interactive rebase editor (with the list of commits) was shown, this is true.
   * In that case, the class expects only unstructured editor to edit the commit message.
   */
  protected boolean myRebaseEditorShown = false;

  private boolean myCommitListCancelled;
  private boolean myUnstructuredEditorCancelled;
  private final @NotNull GitRewordedCommitMessageProvider myRewordedCommitMessageProvider;

  public GitInteractiveRebaseEditorHandler(@NotNull Project project, @NotNull VirtualFile root) {
    myProject = project;
    myRoot = root;
    myRewordedCommitMessageProvider = GitRewordedCommitMessageProvider.getInstance(project);
  }

  @Override
  public int editCommits(@NotNull File file) {
    try {
      if (myRebaseEditorShown) {
        String encoding = GitConfigUtil.getCommitEncoding(myProject, myRoot);
        String originalMessage = FileUtil.loadFile(file, encoding);
        String newMessage = myRewordedCommitMessageProvider.getRewordedCommitMessage(myProject, myRoot, originalMessage);
        if (newMessage == null) {
          myUnstructuredEditorCancelled = !handleUnstructuredEditor(file);
          return myUnstructuredEditorCancelled ? ERROR_EXIT_CODE : 0;
        }
        FileUtil.writeToFile(file, newMessage.getBytes(Charset.forName(encoding)));
        return 0;
      }
      else {
        setRebaseEditorShown();
        boolean success = handleInteractiveEditor(file);
        if (success) {
          return 0;
        }
        else {
          myCommitListCancelled = true;
          return ERROR_EXIT_CODE;
        }
      }
    }
    catch (VcsException e) {
      LOG.error("Failed to load commit details for commits from git rebase file: " + file, e);
      return ERROR_EXIT_CODE;
    }
    catch (Exception e) {
      LOG.error("Failed to edit git rebase file: " + file, e);
      return ERROR_EXIT_CODE;
    }
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

  @Nullable
  protected List<? extends GitRebaseEntry> collectNewEntries(@NotNull List<GitRebaseEntry> entries) throws VcsException {
    Ref<List<? extends GitRebaseEntry>> newText = Ref.create();
    List<GitRebaseEntryWithDetails> entriesWithDetails = loadDetailsForEntries(entries);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      newText.set(showInteractiveRebaseDialog(entriesWithDetails));
    });
    return newText.get();
  }

  @Nullable
  private List<? extends GitRebaseEntry> showInteractiveRebaseDialog(List<GitRebaseEntryWithDetails> entriesWithDetails) {
    GitInteractiveRebaseDialog<GitRebaseEntryWithDetails> editor = new GitInteractiveRebaseDialog<>(myProject, myRoot, entriesWithDetails);
    DialogManager.show(editor);
    if (editor.isOK()) {
      GitRebaseTodoModel<GitRebaseEntryWithDetails> rebaseTodoModel = editor.getModel();
      processModel(rebaseTodoModel);
      return convertToEntries(rebaseTodoModel);
    }
    return null;
  }

  protected void processModel(@NotNull GitRebaseTodoModel<? extends GitRebaseEntryWithDetails> rebaseTodoModel) {
    processModel(rebaseTodoModel, (entry) -> entry.getCommitDetails().getFullMessage());
  }

  protected <T extends GitRebaseEntry> void processModel(
    @NotNull GitRebaseTodoModel<T> rebaseTodoModel,
    @NotNull Function<T, String> fullMessageGetter
  ) {
    List<RewordedCommitMessageMapping> messages = new ArrayList<>();
    for (GitRebaseTodoModel.Element<T> element : rebaseTodoModel.getElements()) {
      if (element.getType() instanceof GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword) {
        GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword type = (GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword)element.getType();
        messages.add(RewordedCommitMessageMapping.fromMapping(
          fullMessageGetter.apply(element.getEntry()),
          type.getNewMessage()
        ));
      }
    }
    myRewordedCommitMessageProvider.save(myProject, myRoot, messages);
  }

  @NotNull
  private List<GitRebaseEntryWithDetails> loadDetailsForEntries(@NotNull List<GitRebaseEntry> entries) throws VcsException {
    List<? extends VcsCommitMetadata> details = GitLogUtil.collectMetadata(
      myProject,
      myRoot,
      ContainerUtil.map(entries, entry -> entry.getCommit())
    );
    List<GitRebaseEntryWithDetails> entriesWithDetails = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      entriesWithDetails.add(new GitRebaseEntryWithDetails(entries.get(i), details.get(i)));
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
  public boolean wasCommitListEditorCancelled() {
    return myCommitListCancelled;
  }

  @Override
  public boolean wasUnstructuredEditorCancelled() {
    return myUnstructuredEditorCancelled;
  }
}
