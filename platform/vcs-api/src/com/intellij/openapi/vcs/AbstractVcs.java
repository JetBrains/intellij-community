/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeListEditHandler;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.VcsAppendableDirtyScope;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The base class for a version control system integrated with IDEA.
 *
 * @see ProjectLevelVcsManager
 */
public abstract class AbstractVcs<ComList extends CommittedChangeList> extends StartedActivated {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.AbstractVcs");

  @NonNls protected static final String ourIntegerPattern = "\\d+";

  protected final Project myProject;
  private final String myName;
  private final VcsKey myKey;
  private VcsShowSettingOption myUpdateOption;
  private VcsShowSettingOption myStatusOption;

  public AbstractVcs(final Project project, final String name) {
    super(project);

    myProject = project;
    myName = name;
    myKey = new VcsKey(myName);
  }

  // acts as adapter
  protected void start() throws VcsException {
  }

  protected void shutdown() throws VcsException {
  }

  protected void activate() {
  }

  protected void deactivate() {
  }

  @NonNls
  public final String getName() {
    return myName;
  }

  @NonNls
  public abstract String getDisplayName();

  public abstract Configurable getConfigurable();

  @Nullable
  public TransactionProvider getTransactionProvider() {
    return null;
  }

  @Nullable
  public ChangeProvider getChangeProvider() {
    return null;
  }

  public final VcsConfiguration getConfiguration() {
    return VcsConfiguration.getInstance(myProject);
  }

  /**
   * Returns the interface for performing check out / edit file operations.
   *
   * @return the interface implementation, or null if none is provided.
   */
  @Nullable
  public EditFileProvider getEditFileProvider() {
    return null;
  }

  public void directoryMappingChanged() {
  }

  public boolean markExternalChangesAsUpToDate() {
    return false;
  }

  /**
   * Returns the interface for performing checkin / commit / submit operations.
   *
   * @return the checkin interface, or null if checkins are not supported by the VCS.
   */
  @Nullable
  public CheckinEnvironment getCheckinEnvironment() {
    return null;
  }

  /**
   * Returns the interface for performing revert / rollback operations.
   *
   * @return the rollback interface, or null if rollbacks are not supported by the VCS.
   */
  @Nullable
  public RollbackEnvironment getRollbackEnvironment() {
    return null;
  }

  @Nullable
  public VcsHistoryProvider getVcsHistoryProvider() {
    return null;
  }

  @Nullable
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return null;
  }

  public String getMenuItemText() {
    return getDisplayName();
  }

  /**
   * Returns the interface for performing update/sync operations.
   *
   * @return the update interface, or null if the updates are not supported by the VCS.
   */
  @Nullable
  public UpdateEnvironment getUpdateEnvironment() {
    return null;
  }

  /**
   * Returns true if the specified file path is located under a directory which is managed by this VCS.
   * This method is called only for directories which are mapped to this VCS in the project configuration.
   *
   * @param filePath the path to check.
   * @return true if the path is managed by this VCS, false otherwise.
   */
  public boolean fileIsUnderVcs(FilePath filePath) {
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
    final VirtualFile virtualFile = path.getVirtualFile();
    if (virtualFile != null) {
      final FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
      return fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.ADDED;
    }
    return true;
  }

  public static boolean fileInVcsByFileStatus(final Project project, final FilePath path) {
    final VirtualFile virtualFile = path.getVirtualFile();
    if (virtualFile != null) {
      final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
      return fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.ADDED && fileStatus != FileStatus.IGNORED;
    }
    return true;
  }

  /**
   * Returns the interface for performing "check status" operations (operations which show the differences between
   * the local working copy state and the latest server state).
   *
   * @return the status interface, or null if the check status operation is not supported or required by the VCS.
   */
  @Nullable
  public UpdateEnvironment getStatusEnvironment() {
    return null;
  }

  @Nullable
  public AnnotationProvider getAnnotationProvider() {
    return null;
  }

  @Nullable
  public DiffProvider getDiffProvider() {
    return null;
  }

  public VcsShowSettingOption getUpdateOptions() {
    return myUpdateOption;
  }


  public VcsShowSettingOption getStatusOptions() {
    return myStatusOption;
  }

  public void loadSettings() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

    if (getUpdateEnvironment() != null) {
      myUpdateOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.UPDATE, this);
    }

    if (getStatusEnvironment() != null) {
      myStatusOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.STATUS, this);
    }
  }

  public FileStatus[] getProvidedStatuses() {
    return null;
  }

  /**
   * Returns the interface for selecting file version numbers.
   *
   * @return the revision selector implementation, or null if none is provided.
   * @since 5.0.2
   */
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return null;
  }

  /**
   * Returns the interface for performing integrate operations (merging changes made in another branch of
   * the project into the current working copy).
   *
   * @return the update interface, or null if the integrate operations are not supported by the VCS.
   */
  @Nullable
  public UpdateEnvironment getIntegrateEnvironment() {
    return null;
  }

  @Nullable
  public CommittedChangesProvider getCommittedChangesProvider() {
    return null;
  }

  @Nullable
  public final CachingCommittedChangesProvider getCachingCommittedChangesProvider() {
    CommittedChangesProvider provider = getCommittedChangesProvider();
    if (provider instanceof CachingCommittedChangesProvider) {
      return (CachingCommittedChangesProvider)provider;
    }
    return null;
  }

  /**
   * For some version controls (like Git) the revision parsing is dependent
   * on the the specific repository instance since the the revision number
   * returned from this method is later used for comparison information.
   * By default, this method invokes {@link #parseRevisionNumber(String)}.
   * The client code should invoke this method, if it expect ordering information
   * from revision numbers.
   *
   * @param revisionNumberString the string to be parsed
   * @param path                 the path for which revsion number is queried
   * @return the parsed revision number
   */
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revisionNumberString, FilePath path) {
    return parseRevisionNumber(revisionNumberString);
  }

  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revisionNumberString) {
    return null;
  }

  /**
   * @return null if does not support revision parsing
   */
  @Nullable
  public String getRevisionPattern() {
    return null;
  }

  /**
   * Checks if the specified directory is managed by this version control system (regardless of the
   * project VCS configuration). For example, for CVS this checks the presense of "CVS" admin directories.
   * This method is used for VCS autodetection during initial project creation and VCS configuration.
   *
   * @param dir the directory to check.
   * @return <code>true</code> if directory is managed by this VCS
   */
  public boolean isVersionedDirectory(VirtualFile dir) {
    return false;
  }

  /**
   * If VCS does not implement detection whether directory is versioned ({@link #isVersionedDirectory(com.intellij.openapi.vfs.VirtualFile)}),
   * it should return <code>false</code>. Otherwise return <code>true</code>
   */
  public boolean supportsVersionedStateDetection() {
    return true;
  }

  /**
   * Returns the configurable to be shown in the VCS directory mapping dialog which should be displayed
   * for configuring VCS-specific settings for the specified root, or null if no such configuration is required.
   * The VCS-specific settings are stored in {@link com.intellij.openapi.vcs.VcsDirectoryMapping#getRootSettings()}.
   *
   * @param mapping the mapping being configured
   * @return the configurable instance, or null if no configuration is required.
   */
  @Nullable
  public UnnamedConfigurable getRootConfigurable(VcsDirectoryMapping mapping) {
    return null;
  }

  @Nullable
  public VcsRootSettings createEmptyVcsRootSettings() {
    return null;
  }

  @Nullable
  public RootsConvertor getCustomConvertor() {
    return null;
  }

  public interface RootsConvertor {
    List<VirtualFile> convertRoots(List<VirtualFile> result);
  }

  /**
   * Returns the implementation of the merge provider which is used to load the revisions to be merged
   * for a particular file.
   *
   * @return the merge provider implementation, or null if the VCS doesn't support merge operations.
   */
  @Nullable
  public MergeProvider getMergeProvider() {
    return null;
  }

  /**
   * List of actions that would be added to local changes browser if there are any changes for this VCS
   */
  @Nullable
  public List<AnAction> getAdditionalActionsForLocalChange() {
    return null;
  }

  @Nullable
  public ChangeListEditHandler getEditHandler() {
    return null;
  }

  public boolean allowsNestedRoots() {
    return false;
  }

  public <S> List<S> filterUniqueRoots(final List<S> in, final Convertor<S, VirtualFile> convertor) {
    new FilterDescendantVirtualFileConvertible(convertor, FilePathComparator.getInstance()).doFilter(in);
    return in;
  }

  public static <S> List<S> filterUniqueRootsDefault(final List<S> in, final Convertor<S, VirtualFile> convertor) {
    new FilterDescendantVirtualFileConvertible(convertor, FilePathComparator.getInstance()).doFilter(in);
    return in;
  }

  @Nullable
  public VcsExceptionsHotFixer getVcsExceptionsHotFixer() {
    return null;
  }

  // for VCSes, that tracks their dirty scopes themselves - for instance, P4
  public VcsAppendableDirtyScope adjustDirtyScope(final VcsAppendableDirtyScope scope) {
    return scope;
  }

  public Project getProject() {
    return myProject;
  }

  protected static VcsKey createKey(final String name) {
    return new VcsKey(name);
  }

  public final VcsKey getKeyInstanceMethod() {
    return myKey;
  }

  public VcsType getType() {
    return VcsType.centralized;
  }

  // todo ?
  public boolean checkImmediateParentsBeforeCommit() {
    return false;
  }

  @Nullable
  protected VcsOutgoingChangesProvider<ComList> getOutgoingProviderImpl() {
    return null;
  }

  @Nullable
  public final VcsOutgoingChangesProvider<ComList> getOutgoingChangesProvider() {
    return VcsType.centralized.equals(getType()) ? null : getOutgoingProviderImpl();
  }

  public RemoteDifferenceStrategy getRemoteDifferenceStrategy() {
    return RemoteDifferenceStrategy.ASK_LATEST_REVISION;
  }

  public boolean areDirectoriesVersionedItems() {
    return false;
  }

  @Nullable
  protected TreeDiffProvider getTreeDiffProviderImpl() {
    return null;
  }

  @Nullable
  public TreeDiffProvider getTreeDiffProvider() {
    final RemoteDifferenceStrategy strategy = getRemoteDifferenceStrategy();
    return RemoteDifferenceStrategy.ASK_LATEST_REVISION.equals(strategy) ? null : getTreeDiffProviderImpl();
  }

  public List<CommitExecutor> getCommitExecutors() {
    return Collections.emptyList();
  }

  /**
   * Can be temporarily forbidden, for instance, when authorization credentials are wrong - to
   * don't repeat wrong credentials passing (in some cases it can produce user's account blocking)
   */
  public boolean isVcsBackgroundOperationsAllowed(final VirtualFile root) {
    return true;
  }

  /**
   * Returns all files for a revision where
   *
   * @param revision revision
   * @param file one of files from revision
   * @return committed change list from where you can get all committed files
   * @throws VcsException if VCS does not support such operation
   *
   * @since 9.0.2
   */
  @Nullable
  public CommittedChangeList getRevisionChanges(final VcsFileRevision revision, final VirtualFile file) throws VcsException {
    throw new RevisionChangesNotSupportedException();
  }

  /**
   * will be useful for Clear Case
   */
  @CalledInAwt
  public void generalPreConfigurationStep() {
  }
}

