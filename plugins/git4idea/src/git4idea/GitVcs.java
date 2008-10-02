package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

import git4idea.providers.GitAnnotationProvider;
import git4idea.providers.GitChangeProvider;
import git4idea.providers.GitDiffProvider;
import git4idea.providers.GitHistoryProvider;
import git4idea.providers.GitRefactoringListenerProvider;
import git4idea.vfs.GitRevisionNumber;
import git4idea.vfs.GitRevisionSelector;
import git4idea.vfs.GitVirtualFile;
import git4idea.vfs.GitVirtualFileAdapter;
import git4idea.envs.GitCheckinEnvironment;
import git4idea.envs.GitRollbackEnvironment;
import git4idea.envs.GitUpdateEnvironment;
import git4idea.config.GitVcsConfigurable;
import git4idea.config.GitVcsSettings;
import git4idea.changes.ChangeMonitor;
import git4idea.i18n.GitBundle;

/**
 * Git VCS implementation
 */
public class GitVcs extends AbstractVcs implements Disposable {
    private static final String GIT = "Git";
    private ChangeProvider changeProvider;
    private VcsShowConfirmationOption addConfirmation;
    private VcsShowConfirmationOption delConfirmation;

    private CheckinEnvironment checkinEnvironment;
    private RollbackEnvironment rollbackEnvironment;
    private GitUpdateEnvironment updateEnvironment;

    private GitAnnotationProvider annotationProvider;
    private DiffProvider diffProvider;
    private VcsHistoryProvider historyProvider;
    private Disposable activationDisposable;
    private final ProjectLevelVcsManager vcsManager;
    private final GitVcsSettings settings;
    private EditorColorsScheme editorColorsScheme;
    private Configurable configurable;
    private RevisionSelector revSelector;
    private GitVirtualFileAdapter gitFileAdapter;
    private RefactoringElementListenerProvider renameListenerProvider;

    public static GitVcs getInstance(@NotNull Project project) {
        return (GitVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(GIT);
    }

    public GitVcs(
            @NotNull Project project,
            @NotNull final GitChangeProvider gitChangeProvider,
            @NotNull final GitCheckinEnvironment gitCheckinEnvironment,
            @NotNull final ProjectLevelVcsManager gitVcsManager,
            @NotNull final GitAnnotationProvider gitAnnotationProvider,
            @NotNull final GitDiffProvider gitDiffProvider,
            @NotNull final GitHistoryProvider gitHistoryProvider,
            @NotNull final GitRollbackEnvironment gitRollbackEnvironment,
            @NotNull final GitVcsSettings gitSettings) {
        super(project);

        vcsManager = gitVcsManager;
        settings = gitSettings;
        addConfirmation = gitVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
        delConfirmation = gitVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);
        changeProvider = gitChangeProvider;
        checkinEnvironment = gitCheckinEnvironment;
        annotationProvider = gitAnnotationProvider;
        diffProvider = gitDiffProvider;
        editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
        historyProvider = gitHistoryProvider;
        rollbackEnvironment = gitRollbackEnvironment;
        revSelector = new GitRevisionSelector();
        configurable = new GitVcsConfigurable(settings, myProject);
        updateEnvironment = new GitUpdateEnvironment(myProject, settings, configurable);

        ((GitCheckinEnvironment) checkinEnvironment).setProject(myProject);
        ((GitCheckinEnvironment) checkinEnvironment).setSettings(settings);
        renameListenerProvider = new GitRefactoringListenerProvider();
    }

    @Override
    public String getName() {
        return GIT;
    }

    @Override
    @NotNull
    public CheckinEnvironment getCheckinEnvironment() {
        return checkinEnvironment;
    }

    @Override
    @NotNull
    public RollbackEnvironment getRollbackEnvironment() {
        return rollbackEnvironment;
    }

    @Override
    @NotNull
    public VcsHistoryProvider getVcsHistoryProvider() {
        return historyProvider;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return GIT;
    }

    @Override
    @Nullable
    public UpdateEnvironment getUpdateEnvironment() {
        return updateEnvironment;
    }

    @Override
    @Nullable
    public UpdateEnvironment getStatusEnvironment() {
        return getUpdateEnvironment();
    }

    @Override
    @NotNull
    public GitAnnotationProvider getAnnotationProvider() {
        return annotationProvider;
    }

    @Override
    @NotNull
    public DiffProvider getDiffProvider() {
        return diffProvider;
    }

    @Override
    @Nullable
    public RevisionSelector getRevisionSelector() {
        return revSelector;
    }

    @Override
    public UpdateEnvironment getIntegrateEnvironment() {
        return getUpdateEnvironment();
    }

    @SuppressWarnings({"deprecation"})
    @Override
    @Nullable
    public VcsRevisionNumber parseRevisionNumber(String revision) {
        if (revision == null || revision.length() == 0) return null;

        if (revision.length() > 40) {    // date & revision-id encoded string
            String datestr = revision.substring(0, revision.indexOf("["));
            String rev = revision.substring(revision.indexOf("[") + 1, 40);
            Date d = new Date(Date.parse(datestr));
            return new GitRevisionNumber(rev, d);
        }

        return new GitRevisionNumber(revision);
    }

    @Override
    public boolean isVersionedDirectory(VirtualFile dir) {
        final VirtualFile versionFile = dir.findChild(".git");
        return versionFile != null && versionFile.isDirectory();
    }

    @Override
    public void shutdown() throws VcsException {
        super.shutdown();
        dispose();
    }

    @Override
    public void activate() {
        super.activate();
        activationDisposable = new Disposable() {
            public void dispose() {
            }
        };
        gitFileAdapter = new GitVirtualFileAdapter(this, myProject);
        VirtualFileManager.getInstance().addVirtualFileListener(gitFileAdapter, activationDisposable);
        LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(gitFileAdapter);
        RefactoringListenerManager.getInstance(myProject).addListenerProvider(renameListenerProvider);
        ChangeMonitor mon = ChangeMonitor.getInstance(myProject);
        mon.setGitVcsSettings(settings);
        mon.start();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(gitFileAdapter);
        RefactoringListenerManager.getInstance(myProject).removeListenerProvider(renameListenerProvider);
        VirtualFileManager.getInstance().removeVirtualFileListener(gitFileAdapter);
        assert activationDisposable != null;
        Disposer.dispose(activationDisposable);
        activationDisposable = null;
        ChangeMonitor.getInstance(myProject).stopRunning();
    }

    @NotNull
    public VcsShowConfirmationOption getAddConfirmation() {
        return addConfirmation;
    }

    @NotNull
    public VcsShowConfirmationOption getDeleteConfirmation() {
        return delConfirmation;
    }

    @NotNull
    @Override
    public Configurable getConfigurable() {
        return configurable;
    }

    @Nullable
    public ChangeProvider getChangeProvider() {
        return changeProvider;
    }

    public void showErrors(@NotNull java.util.List<VcsException> list, @NotNull String action) {
        if (list.size() > 0) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("\n");
            buffer.append(action).append(" " + GitBundle.message("error") + " ");
            for (VcsException e : list) {
                if (e != null)
                    buffer.append(e.getMessage() + "\n");
            }

            String msg = buffer.toString();
            showMessage(msg, CodeInsightColors.ERRORS_ATTRIBUTES);
            Messages.showErrorDialog(myProject, msg, GitBundle.message("error"));
        }
    }

    public void showMessages(@NotNull String message) {
        if (message.length() == 0)
            return;
        showMessage(message, HighlighterColors.TEXT);
    }

    @NotNull
    public GitVcsSettings getSettings() {
        return settings;
    }

    private void showMessage(@NotNull String message, final TextAttributesKey text) {
        vcsManager.addMessageToConsoleWindow(message, editorColorsScheme.getAttributes(text));
    }

    public void dispose() {
        assert activationDisposable == null;
    }

    public GitVirtualFileAdapter getFileAdapter() {
        return gitFileAdapter;
    }

    /**
     * Returns true if the specified file path is located under a directory which is managed by this VCS.
     * This method is called only for directories which are mapped to this VCS in the project configuration.
     *
     * @param filePath the path to check.
     * @return true if the path is managed by this VCS, false otherwise.
     */
    public boolean fileIsUnderVcs(FilePath filePath) {
        if (filePath == null) return false;
        String pathName = filePath.getPath();
        if (pathName.contains("\\.git\\") || pathName.contains("/.git/"))
            return false;
        else
            return true;
    }

    /**
     * Returns true if the specified file path represents a file which exists in the VCS repository (is neither
     * unversioned nor scheduled for addition).
     * This method is called only for directories which are mapped to this VCS in the project configuration.
     *
     * @param path the path to check.
     * @return true if the corresponding file exists in the repository, false otherwise.
     */
    public boolean fileExistsInVcs(FilePath path) {
        GitVirtualFile file = new GitVirtualFile(myProject, path.getPath());
        return gitFileAdapter.isFileProcessable(file);
    }
}