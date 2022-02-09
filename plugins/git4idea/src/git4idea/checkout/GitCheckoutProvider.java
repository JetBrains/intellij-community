// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkout;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProviderEx;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.commands.GitStandardProgressAnalyzer;
import git4idea.ui.GitCloneDialogComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.GitNotificationIdsHolder.CLONE_FAILED;

/**
 * Checkout provider for the Git
 */
public final class GitCheckoutProvider extends CheckoutProviderEx {
  private static final Logger LOG = Logger.getInstance(GitCheckoutProvider.class);

  private static final List<@NonNls String> NON_ERROR_LINE_PREFIXES = Arrays.asList("Cloning into", "remote:", "submodule");

  @Override
  public String getVcsName() {
    return "_Git";
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
    clone(project, Git.getInstance(), listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory);
  }

  public static void clone(final Project project, @NotNull final Git git, final Listener listener, final VirtualFile destinationParent,
                           final String sourceRepositoryURL, final String directoryName, final String parentDirectory) {

    final AtomicBoolean cloneResult = new AtomicBoolean();
    new Task.Backgroundable(project, DvcsBundle.message("cloning.repository", sourceRepositoryURL)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        cloneResult.set(doClone(project, git, directoryName, parentDirectory, sourceRepositoryURL));
      }

      @Override
      public void onSuccess() {
        boolean success = cloneResult.get();
        File directory = new File(parentDirectory, directoryName);
        LOG.debug(String.format("Cloned into %s with success=%s", directory, success));
        if (!success) {
          return;
        }

        DvcsUtil.addMappingIfSubRoot(project, directory.getPath(), GitVcs.NAME);
        destinationParent.refresh(true, true, () -> {
          if (project.isOpen() && (!project.isDisposed()) && !project.isDefault()) {
            VcsDirtyScopeManager.getInstance(project).fileDirty(destinationParent);
          }
        });

        listener.directoryCheckedOut(directory, GitVcs.getKey());
        listener.checkoutCompleted();
      }
    }.queue();
  }

  public static boolean doClone(@NotNull Project project, @NotNull Git git,
                                @NotNull String directoryName, @NotNull String parentDirectory, @NotNull String sourceRepositoryURL) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(false);
    GitLineHandlerListener progressListener = GitStandardProgressAnalyzer.createListener(indicator);
    GitCommandResult result = git.clone(project, new File(parentDirectory), sourceRepositoryURL, directoryName, progressListener);
    if (result.success()) {
      return true;
    }
    List<@NlsSafe String> errorLines = ContainerUtil.filter(result.getErrorOutput(), line ->
      !ContainerUtil.exists(NON_ERROR_LINE_PREFIXES, prefix -> StringUtil.startsWithIgnoreCase(line, prefix)));
    List<HtmlChunk> displayErrorLines = ContainerUtil.map(errorLines, msg -> HtmlChunk.text(GitUtil.cleanupErrorPrefixes(msg)));
    String description = new HtmlBuilder().appendWithSeparators(HtmlChunk.br(), displayErrorLines).toString();
    VcsNotifier.getInstance(project).notifyError(CLONE_FAILED, DvcsBundle.message("error.title.cloning.repository.failed"), description, true);
    return false;
  }

  @Override
  @NotNull
  public String getVcsId() {
    return GitVcs.ID;
  }

  @Override
  public void doCheckout(@NotNull Project project,
                         @Nullable Listener listener) {
    doCheckout(project, listener, null);
  }

  @NotNull
  @Override
  public VcsCloneComponent buildVcsCloneComponent(@NotNull Project project, @NotNull ModalityState modalityState, @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
    return new GitCloneDialogComponent(project, modalityState, dialogStateListener);
  }
}
