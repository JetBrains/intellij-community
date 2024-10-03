// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkout;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProviderEx;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTask;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTaskInfo;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitCloneDialogComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static git4idea.GitNotificationIdsHolder.CLONE_FAILED;

/**
 * Checkout provider for the Git
 */
public final class GitCheckoutProvider extends CheckoutProviderEx {
  private static final Logger LOG = Logger.getInstance(GitCheckoutProvider.class);

  private static final List<@NonNls String> NON_ERROR_LINE_PREFIXES = Arrays.asList("Cloning into", "remote:", "submodule");

  @Override
  public @NotNull String getVcsName() {
    return GitBundle.message("git4idea.vcs.name.with.mnemonic");
  }

  @Override
  public void doCheckout(@NotNull Project project, @Nullable Listener listener, @Nullable String predefinedRepositoryUrl) {
    FileDocumentManager.getInstance().saveAllDocuments();
    GitCloneDialog dialog = new GitCloneDialog(project, predefinedRepositoryUrl);
    if (!dialog.showAndGet()) {
      return;
    }

    dialog.rememberSettings();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final File parent = new File(dialog.getParentDirectory());
    VirtualFile destinationParent = lfs.findFileByIoFile(parent);
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent);
    }
    if (destinationParent == null) {
      return;
    }
    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    final String directoryName = dialog.getDirectoryName();
    final String parentDirectory = dialog.getParentDirectory();
    clone(project, Git.getInstance(), listener, destinationParent, sourceRepositoryURL,
          directoryName, parentDirectory);
  }

  public static void clone(final @NotNull Project project,
                           final @NotNull Git git,
                           final Listener listener,
                           final VirtualFile destinationParent,
                           final String sourceRepositoryURL,
                           final String directoryName,
                           final String parentDirectory) {
    clone(project, git, listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory, null);
  }

  public static void clone(final @NotNull Project project, final @NotNull Git git, final Listener listener,
                           final VirtualFile destinationParent,
                           final String sourceRepositoryURL,
                           final String directoryName,
                           final String parentDirectory,
                           final GitShallowCloneOptions shallowCloneOptions) {
    String projectAbsolutePath = Paths.get(parentDirectory, directoryName).toAbsolutePath().toString();
    String projectPath = FileUtilRt.toSystemIndependentName(projectAbsolutePath);

    CloneTask cloneTask = new CloneTask() {

      @Override
      public @NotNull CloneTaskInfo taskInfo() {
        return new CloneTaskInfo(DvcsBundle.message("cloning.repository", sourceRepositoryURL),
                                 DvcsBundle.message("cloning.repository.cancel", sourceRepositoryURL),
                                 DvcsBundle.message("clone.repository"),
                                 DvcsBundle.message("clone.repository.tooltip"),
                                 DvcsBundle.message("clone.repository.failed"),
                                 DvcsBundle.message("clone.repository.canceled"),
                                 DvcsBundle.message("clone.stop.message.title"),
                                 DvcsBundle.message("clone.stop.message.description", sourceRepositoryURL));
      }

      @Override
      public @NotNull CloneStatus run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        GitLineHandlerListener progressListener = GitStandardProgressAnalyzer.createListener(indicator);

        GitCommandResult result;
        try {
          result = git.clone(project, new File(parentDirectory), sourceRepositoryURL, directoryName, shallowCloneOptions, progressListener);
        }
        catch (Exception e) {
          if (listener instanceof GitCheckoutListener) {
            ((GitCheckoutListener) listener).checkoutFailed(null);
          }
          throw e;
        }

        if (result.success()) {
          File directory = new File(parentDirectory, directoryName);
          LOG.debug(String.format("Cloned into %s with success=%s", directory, result));

          DvcsUtil.addMappingIfSubRoot(project, directory.getPath(), GitVcs.NAME);
          destinationParent.refresh(true, true);

          listener.directoryCheckedOut(directory, GitVcs.getKey());
          listener.checkoutCompleted();

          return CloneStatus.SUCCESS;
        }

        notifyError(project, result, sourceRepositoryURL);
        if (listener instanceof GitCheckoutListener) {
          ((GitCheckoutListener) listener).checkoutFailed(result);
        }

        return CloneStatus.FAILURE;
      }
    };

    CloneableProjectsService.getInstance().runCloneTask(projectPath, cloneTask);
  }

  public static boolean doClone(@NotNull Project project,
                                @NotNull Git git,
                                @NotNull String directoryName,
                                @NotNull String parentDirectory,
                                @NotNull String sourceRepositoryURL) {
    return doClone(project, git, directoryName, parentDirectory, sourceRepositoryURL, null);
  }

  public static boolean doClone(@NotNull Project project,
                                @NotNull Git git,
                                @NotNull String directoryName,
                                @NotNull String parentDirectory,
                                @NotNull String sourceRepositoryURL,
                                @Nullable GitShallowCloneOptions shallowCloneOptions) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(false);

    GitLineHandlerListener progressListener = GitStandardProgressAnalyzer.createListener(indicator);
    GitCommandResult result = git.clone(project, new File(parentDirectory), sourceRepositoryURL, directoryName, shallowCloneOptions, progressListener);
    if (result.success()) {
      return true;
    }

    notifyError(project, result, sourceRepositoryURL);

    return false;
  }

  private static void notifyError(@NotNull Project project, @NotNull GitCommandResult commandResult, @NotNull String sourceRepositoryURL) {
    List<@NlsSafe String> errorLines = ContainerUtil.filter(commandResult.getErrorOutput(), line ->
      !ContainerUtil.exists(NON_ERROR_LINE_PREFIXES, prefix -> StringUtil.startsWithIgnoreCase(line, prefix)));

    String description;
    if (errorLines.isEmpty()) {
      description = DvcsBundle.message("error.description.cloning.repository.failed", sourceRepositoryURL);
    }
    else {
      List<HtmlChunk> displayErrorLines = ContainerUtil.map(errorLines, msg -> HtmlChunk.text(GitUtil.cleanupErrorPrefixes(msg)));
      description = new HtmlBuilder().appendWithSeparators(HtmlChunk.br(), displayErrorLines).toString();
    }

    VcsNotifier.getInstance(project)
      .notifyError(CLONE_FAILED, DvcsBundle.message("error.title.cloning.repository.failed"), description, true);
  }

  @Override
  public @NotNull String getVcsId() {
    return GitVcs.ID;
  }

  @Override
  public void doCheckout(@NotNull Project project,
                         @Nullable Listener listener) {
    doCheckout(project, listener, null);
  }

  @Override
  public @NotNull VcsCloneComponent buildVcsCloneComponent(@NotNull Project project,
                                                           @NotNull ModalityState modalityState,
                                                           @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
    return new GitCloneDialogComponent(project, modalityState, dialogStateListener);
  }
}
