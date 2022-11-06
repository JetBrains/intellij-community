// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsBaseRevisionAdviser;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.VcsSynchronousProgressWrapper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The base class for a version control system.
 *
 * @see ProjectLevelVcsManager
 */
public abstract class AbstractVcs extends StartedActivated {
  @NonNls protected static final String ourIntegerPattern = "\\d+";

  @NotNull
  protected final Project myProject;
  private final String myName;
  private final VcsKey myKey;

  private CheckinEnvironment myCheckinEnvironment;
  private UpdateEnvironment myUpdateEnvironment;
  private RollbackEnvironment myRollbackEnvironment;

  public AbstractVcs(@NotNull Project project, @NonNls String name) {
    myProject = project;
    myName = name;
    myKey = new VcsKey(myName);
  }

  /**
   * Called when VCS plugin is loaded.
   * Typically, {@link #activate()} or {@link AbstractVcs#AbstractVcs} should be used instead.
   *
   * @see #shutdown
   */
  @Override
  protected void start() throws VcsException {
  }

  /**
   * Called when VCS plugin is unloaded. Typically, {@link #deactivate()} should be used instead.
   */
  @Override
  protected void shutdown() {
  }

  /**
   * Called when VCS gets a configured mapping.
   * <p/>
   * This is a good place to install project-wide listeners and perform generic initialization.
   *
   * @see #deactivate
   */
  @Override
  protected void activate() {
  }

  /**
   * Called when VCS no longer has configured mappings.
   */
  @Override
  protected void deactivate() {
  }

  /**
   * Unique internal ID for VCS.
   *
   * @see com.intellij.openapi.vcs.impl.VcsEP#name
   * @see VcsKey#getName
   * @see AllVcsesI#getByName(String)
   */
  @NonNls
  public final String getName() {
    return myName;
  }

  /**
   * Returns the name of the VCS as it should be displayed in the UI.
   *
   * @see #getShortName
   */
  @Nls
  @NotNull
  public abstract String getDisplayName();

  /**
   * Returns the short or abbreviated name of this VCS, which name can be used in those places in the UI where the space is limited.
   * (e.g. it can be "SVN" for Subversion or "Hg" for Mercurial).<br/><br/>
   */
  @Nls
  @NotNull
  public String getShortName() {
    return getDisplayName();
  }

  /**
   * Returns the short or abbreviated name of this VCS, with mnemonic, which name can be used in menus and action names.
   * (e.g. it can be "_SVN" for Subversion or "_Hg" for Mercurial).<br/><br/>
   * Returns generic "VC_S" by default.
   */
  @Nls
  @NotNull
  public String getShortNameWithMnemonic() {
    return VcsBundle.message("vcs.generic.name.with.mnemonic");
  }

  /**
   * Allows hiding the 'VCS' action group in 'Main Menu'.
   * Takes effect for projects that have configured mappings for this VCS only.
   *
   * @return true if the 'VCS' group should be hidden.
   */
  public boolean isWithCustomMenu() {
    return false;
  }

  /**
   * Allows to hide 'Local Changes' toolwindow tab, as well as disable changelists.
   * Takes effect for projects that have configured mappings for this VCS only.
   *
   * @return true if 'Local Changes' tab should be hidden.
   */
  public boolean isWithCustomLocalChanges() {
    return false;
  }

  /**
   * Allows to hide 'Shelf' toolwindow tab.
   * Takes effect for projects that have configured mappings for this VCS only.
   *
   * @return true if 'Shelf' tab should be hidden.
   */
  public boolean isWithCustomShelves() {
    return false;
  }

  /**
   * @return Custom value for {@link com.intellij.openapi.vcs.actions.CompareWithTheSameVersionAction} action text.
   */
  @NlsActions.ActionText
  @Nullable
  public String getCompareWithTheSameVersionActionName() {
    return null;
  }

  @Nullable
  public Configurable getConfigurable() {
    return null;
  }

  @Nullable
  public TransactionProvider getTransactionProvider() {
    return null;
  }

  /**
   * Used by {@link com.intellij.openapi.vcs.changes.ChangeListManager} to collect changed files for VCS.
   * <p/>
   * VCS should notify {@link com.intellij.openapi.vcs.changes.VcsDirtyScopeManager} if it expects that some file statuses were changed,
   * so that {@link ChangeProvider} could be called again for these files.
   *
   * @see #needsCaseSensitiveDirtyScope
   */
  @Nullable
  public ChangeProvider getChangeProvider() {
    return null;
  }

  public final VcsConfiguration getConfiguration() {
    return VcsConfiguration.getInstance(myProject);
  }

  /**
   * Returns the interface for performing check-out / edit file operations.
   *
   * @return the interface implementation, or null if none is provided.
   */
  @Nullable
  public EditFileProvider getEditFileProvider() {
    return null;
  }

  /**
   * @deprecated dead code
   */
  @Deprecated(forRemoval = true)
  public boolean markExternalChangesAsUpToDate() {
    return false;
  }

  /**
   * Returns the interface for performing checkin / commit / submit operations.
   *
   * @return the checkin interface, or null if checkins are not supported by the VCS.
   * @see #getCommitExecutors
   * @see #arePartialChangelistsSupported
   */
  @Nullable
  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  /**
   * @return the rollback interface, or null if rollbacks are not supported by the VCS.
   */
  @Nullable
  public RollbackEnvironment getRollbackEnvironment() {
    return myRollbackEnvironment;
  }

  /**
   * @see #getDiffProvider
   * @see #getCommittedChangesProvider
   */
  @Nullable
  public VcsHistoryProvider getVcsHistoryProvider() {
    return null;
  }

  /**
   * Typically, delegates to {@link #getVcsHistoryProvider}.
   */
  @Nullable
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return null;
  }

  /**
   * @deprecated dead code
   */
  @Deprecated(forRemoval = true)
  public String getMenuItemText() {
    return getDisplayName();
  }

  /**
   * @return the update interface, or null if the updates are not supported by the VCS.
   * @see #getStatusEnvironment
   * @see #getIntegrateEnvironment
   * @see #getVcsExceptionsHotFixer
   */
  @Nullable
  public UpdateEnvironment getUpdateEnvironment() {
    return myUpdateEnvironment;
  }

  /**
   * Returns true if the specified file path is located under a directory which is managed by this VCS.
   * This method is called only for directories which are mapped to this VCS in the project configuration.
   *
   * @param filePath the path to check.
   * @return true if the path is managed by this VCS, false otherwise.
   * @deprecated Use {@link VcsRootChecker} instead.
   */
  @Deprecated
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
    VirtualFile virtualFile = path.getVirtualFile();
    if (virtualFile != null) {
      FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
      return fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.ADDED;
    }
    return true;
  }

  /**
   * This method is called when user invokes "Enable VCS Integration" and selects a particular VCS.
   * <p/>
   * By default, it sets up a single mapping {@code <Project> -> selected VCS}.
   */
  @RequiresEdt
  public void enableIntegration() {
    enableIntegration(null);
  }

  /**
   * This method is called when a user invokes "Enable VCS Integration" and selects a particular VCS.
   * <p/>
   * By default, it sets up a single mapping {@code <targetDirectory> -> selected VCS}.
   * Some VCSes might try to automatically detect VCS roots or create a new one.
   *
   * @param targetDirectory overridden location of project files to check
   */
  @RequiresEdt
  public void enableIntegration(@Nullable VirtualFile targetDirectory) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (vcsManager != null) {
      if (targetDirectory != null) {
        vcsManager.setDirectoryMappings(Collections.singletonList(new VcsDirectoryMapping(targetDirectory.getPath(), getName())));
      }
      else {
        vcsManager.setDirectoryMappings(Collections.singletonList(VcsDirectoryMapping.createDefault(getName())));
      }
    }
  }

  /**
   * Invoked when a changelist is deleted explicitly by user or implicitly (e.g. after default changelist switch
   * when the previous one was empty).
   *
   * @param list       change list that's about to be removed
   * @param explicitly whether it's a result of an explicit Delete action, or just triggered after switching active changelist.
   * @return UNSURE if the VCS has nothing to say about this changelist.
   * YES or NO if the changelist has to be removed or not, and no further confirmations are needed about this changelist
   * (in particular, the VCS can show a confirmation to the user by itself)
   */
  @RequiresEdt
  @NotNull
  public ThreeState mayRemoveChangeList(@NotNull LocalChangeList list, boolean explicitly) {
    return ThreeState.UNSURE;
  }

  /**
   * @see com.intellij.openapi.vcs.changes.LastUnchangedContentTracker
   */
  public boolean isTrackingUnchangedContent() {
    return false;
  }

  /**
   * @return whether VCS tracks the file. Ie: if requesting 'vcs file history' makes sense.
   */
  public static boolean fileInVcsByFileStatus(@NotNull Project project, @NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    return file == null || fileInVcsByFileStatus(project, file);
  }

  /**
   * @return whether VCS tracks the file. Ie: if requesting 'vcs file history' makes sense.
   */
  public static boolean fileInVcsByFileStatus(@NotNull Project project, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
    return status != FileStatus.UNKNOWN && status != FileStatus.ADDED && status != FileStatus.IGNORED;
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

  /**
   * Provides information about per-line modification history for a file ('git blame').
   */
  @Nullable
  public AnnotationProvider getAnnotationProvider() {
    return null;
  }

  /**
   * @see #getVcsHistoryProvider
   * @see #getRevisionSelector
   */
  @Nullable
  public DiffProvider getDiffProvider() {
    return null;
  }

  /**
   * Notify that VCS supports some standard options.
   * This information is used to hide options from settings if no available VCS supports them.
   *
   * @see ProjectLevelVcsManager#getStandardOption
   * @see ProjectLevelVcsManager#getStandardConfirmation
   */
  public void loadSettings() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

    if (getUpdateEnvironment() != null) {
      vcsManager.getStandardOption(VcsConfiguration.StandardOption.UPDATE, this);
    }

    if (getStatusEnvironment() != null) {
      vcsManager.getStandardOption(VcsConfiguration.StandardOption.STATUS, this);
    }
  }

  /**
   * Notify that VCS supports some custom file statuses.
   *
   * @return unused - this method is a reminder to call {@link FileStatusFactory#createFileStatus}.
   * @see FileStatusFactory
   */
  @SuppressWarnings("UnusedReturnValue")
  public FileStatus[] getProvidedStatuses() {
    return null;
  }

  /**
   * Returns the interface for selecting file version numbers.
   *
   * @return the revision selector implementation, or null if none is provided.
   */
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return null;
  }

  /**
   * Returns the interface for performing integrate operations (merging changes made in another branch of
   * the project into the current working copy).
   *
   * @return the update interface, or null if integrate operations are not supported by the VCS.
   */
  @Nullable
  public UpdateEnvironment getIntegrateEnvironment() {
    return null;
  }

  /**
   * @see #getVcsHistoryProvider
   */
  public @Nullable CommittedChangesProvider<? extends CommittedChangeList, ?> getCommittedChangesProvider() {
    return null;
  }

  @Nullable
  public final CachingCommittedChangesProvider<? extends CommittedChangeList, ?> getCachingCommittedChangesProvider() {
    CommittedChangesProvider<? extends CommittedChangeList, ?> provider = getCommittedChangesProvider();
    if (provider instanceof CachingCommittedChangesProvider) {
      return (CachingCommittedChangesProvider<? extends CommittedChangeList, ?>)provider;
    }
    return null;
  }

  /**
   * For some version controls (like Git), the revision parsing is dependent
   * on the specific repository instance since the revision number
   * returned from this method is later used for comparison information.
   * By default, this method invokes {@link #parseRevisionNumber(String)}.
   * The client code should invoke this method if it expects ordering information
   * from revision numbers.
   * <p/>
   * Can be used to restore revision information for created patches and shelves.
   * Ex: These revisions may be loaded via {@link VcsBaseRevisionAdviser} or {@link DiffProvider}.
   *
   * @param revisionNumberString the string to be parsed
   * @param path                 the path for which revision number is queried
   * @see #getRevisionPattern
   */
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revisionNumberString, FilePath path) throws VcsException {
    return parseRevisionNumber(revisionNumberString);
  }

  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revisionNumberString) throws VcsException {
    return null;
  }

  /**
   * @return null if VCS does not support revision parsing
   */
  @NonNls
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
   * @return {@code true} if directory is managed by this VCS
   * @deprecated Use {@link VcsRootChecker} instead.
   */
  @Deprecated
  public boolean isVersionedDirectory(VirtualFile dir) {
    return false;
  }

  /**
   * Returns the configurable to be shown in the VCS directory mapping dialog which should be displayed
   * for configuring VCS-specific settings for the specified root, or null if no such configuration is required.
   * The VCS-specific settings are stored in {@link VcsDirectoryMapping#getRootSettings()}.
   *
   * @param mapping the mapping being configured
   * @return the configurable instance, or null if no configuration is required.
   */
  @Nullable
  public UnnamedConfigurable getRootConfigurable(VcsDirectoryMapping mapping) {
    return null;
  }

  /**
   * @see #getRootConfigurable
   */
  @Nullable
  public VcsRootSettings createEmptyVcsRootSettings() {
    return null;
  }

  /**
   * Overrides the list of VCS roots that is returned by {@link ProjectLevelVcsManager#getRootsUnderVcs(AbstractVcs)}.
   *
   * @deprecated This breaks {@link ProjectLevelVcsManager#getRootsUnderVcs(AbstractVcs)} vs {@link ProjectLevelVcsManager#getVcsFor)} symmetry,
   * and should be avoided whenever possible.
   * Consider implementing other means of automatic VCS root detection,
   * such as {@link VcsRootChecker#detectProjectMappings(Project, Collection, Set)} or {@link VcsRootChecker#isRoot(VirtualFile)}.
   */
  @Nullable
  @Deprecated
  public RootsConvertor getCustomConvertor() {
    return null;
  }

  public interface RootsConvertor {

    @NotNull
    List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result);
  }

  /**
   * This switch disables platform support for "default mapping" aka "&lt;Project&gt;".
   * <p/>
   * If enabled, platform will do nothing. All roots from {@link com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy} will be registered as vcs root.
   * Vcs can try using {@link RootsConvertor} to process roots itself.
   * <p/>
   * If disabled, the platform will use {@link VcsRootChecker} or {@link com.intellij.openapi.vcs.impl.VcsEP#administrativeAreaName} to find actual vcs roots.
   * If vcs does not implement these EP, no vcs roots will be registered for "default mapping".
   *
   * @see ProjectLevelVcsManager
   * @see com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
   */
  public boolean needsLegacyDefaultMappings() {
    if (getCustomConvertor() != null) return true;

    for (VcsRootChecker checker : VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList()) {
      if (checker.getSupportedVcs().equals(getKeyInstanceMethod())) return false;
    }

    VcsDescriptor descriptor = AllVcsesI.getInstance(myProject).getDescriptor(myName);
    if (descriptor != null && descriptor.hasVcsDirPattern()) return false;

    return true;
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
   * @return whether VCS can have one mapped root inside another.
   * Typically, this should be overridden.
   */
  public boolean allowsNestedRoots() {
    return false;
  }

  @NotNull
  @Deprecated
  public <S> List<S> filterUniqueRoots(@NotNull List<S> in, @NotNull Function<? super S, ? extends VirtualFile> convertor) {
    if (!allowsNestedRoots()) {
      new FilterDescendantVirtualFileConvertible<>(convertor, FilePathComparator.getInstance()).doFilter(in);
    }
    return in;
  }

  /**
   * Allows customized handling of {@link UpdateSession} errors.
   */
  @Nullable
  public VcsExceptionsHotFixer getVcsExceptionsHotFixer() {
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  protected static VcsKey createKey(@NonNls String name) {
    return new VcsKey(name);
  }

  public final VcsKey getKeyInstanceMethod() {
    return myKey;
  }

  public VcsType getType() {
    return VcsType.centralized;
  }

  @Nullable
  protected VcsOutgoingChangesProvider<CommittedChangeList> getOutgoingProviderImpl() {
    return null;
  }

  @Nullable
  public final VcsOutgoingChangesProvider<CommittedChangeList> getOutgoingChangesProvider() {
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

  public boolean allowsRemoteCalls(@NotNull final VirtualFile file) {
    return true;
  }

  public void setCheckinEnvironment(CheckinEnvironment checkinEnvironment) {
    if (myCheckinEnvironment != null) throw new IllegalStateException("Attempt to redefine checkin environment");
    myCheckinEnvironment = checkinEnvironment;
  }

  public void setUpdateEnvironment(UpdateEnvironment updateEnvironment) {
    if (myUpdateEnvironment != null) throw new IllegalStateException("Attempt to redefine update environment");
    myUpdateEnvironment = updateEnvironment;
  }

  public void setRollbackEnvironment(RollbackEnvironment rollbackEnvironment) {
    if (myRollbackEnvironment != null) throw new IllegalStateException("Attempt to redefine rollback environment");
    myRollbackEnvironment = rollbackEnvironment;
  }

  /**
   * @see #getCheckinEnvironment
   */
  @Nullable
  protected CheckinEnvironment createCheckinEnvironment() {
    return null;
  }

  /**
   * @see #getUpdateEnvironment
   */
  @Nullable
  protected UpdateEnvironment createUpdateEnvironment() {
    return null;
  }

  /**
   * @see #getRollbackEnvironment
   */
  @Nullable
  protected RollbackEnvironment createRollbackEnvironment() {
    return null;
  }

  public void setupEnvironments() {
    setCheckinEnvironment(createCheckinEnvironment());
    setUpdateEnvironment(createUpdateEnvironment());
    setRollbackEnvironment(createRollbackEnvironment());
  }

  @Nullable
  public CommittedChangeList loadRevisions(VirtualFile vf, @NotNull VcsRevisionNumber number) {
    return VcsSynchronousProgressWrapper.compute(() -> {
      CommittedChangesProvider<? extends CommittedChangeList, ?> provider = getCommittedChangesProvider();
      Pair<? extends CommittedChangeList, FilePath> pair = provider == null ? null : provider.getOneList(vf, number);
      return pair == null ? null : pair.getFirst();
    }, getProject(), VcsBundle.message("title.load.revision.contents"));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractVcs that = (AbstractVcs)o;

    if (!myKey.equals(that.myKey)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myKey.hashCode();
  }

  /**
   * @return whether {@link VcsFileListenerContextHelper} should be used when applying a patch on top of a working copy.
   * @see VcsVFSListener
   */
  public boolean fileListenerIsSynchronous() {
    return true;
  }

  /**
   * @return whether VCS supports committing only some changes for a particular file.
   * NB: This mode is incompatible with custom {@link com.intellij.openapi.vcs.impl.LocalLineStatusTrackerProvider}.
   * @see ChangeListChange
   * @see com.intellij.openapi.vcs.impl.PartialChangesUtil
   * @see com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker#handlePartialCommit
   * @see com.intellij.openapi.vcs.impl.LineStatusTrackerManagerI#arePartialChangelistsEnabled()
   */
  public boolean arePartialChangelistsSupported() {
    return false;
  }

  /**
   * @deprecated dead code
   */
  @Deprecated(forRemoval = true)
  public CheckoutProvider getCheckoutProvider() {
    return null;
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * @return whether {@link com.intellij.openapi.vcs.changes.VcsDirtyScopeManager} should preserve file path cases on case-insensitive systems.
   */
  public boolean needsCaseSensitiveDirtyScope() {
    return false;
  }

  /**
   * @return true if VCS root needs to be added to watched roots.
   * @see com.intellij.openapi.vcs.impl.projectlevelman.FileWatchRequestModifier
   */
  public boolean needsLFSWatchesForRoots() {
    return true;
  }
}
