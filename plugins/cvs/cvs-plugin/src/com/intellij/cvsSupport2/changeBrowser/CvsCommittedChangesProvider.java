// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationGroup;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipperAdapter;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import gnu.trove.TObjectLongHashMap;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.log.Revision;

public class CvsCommittedChangesProvider implements CachingCommittedChangesProvider<CvsChangeList, ChangeBrowserSettings> {
  private static final Logger LOG = Logger.getInstance(CvsCommittedChangesProvider.class);

  private final Project myProject;

  public CvsCommittedChangesProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new CvsVersionFilterComponent(showDateFilter);
  }

  @Override
  @Nullable
  public VcsCommittedListsZipper getZipper() {
    return new MyZipper();
  }

  private static class MyZipper extends VcsCommittedListsZipperAdapter {
    private long lastNumber = 1;
    private final TObjectLongHashMap<CommittedChangeListKey> numberCache = new TObjectLongHashMap<>();

    private MyZipper() {
      super(new GroupCreator() {
        @Override
        public Object createKey(final RepositoryLocation location) {
          final CvsRepositoryLocation cvsLocation = (CvsRepositoryLocation) location;
          return cvsLocation.getEnvironment().getRepository();
        }

        @Override
        public RepositoryLocationGroup createGroup(final Object key, final Collection<RepositoryLocation> locations) {
          final RepositoryLocationGroup group = new RepositoryLocationGroup((String) key);
          for (RepositoryLocation location : locations) {
            group.add(location);
          }
          return group;
        }
      });
    }

    @Override
    public long getNumber(@NotNull CommittedChangeList list) {
      final long time = list.getCommitDate().getTime();
      final Long roundedTime = Long.valueOf(time - (time % CvsChangeList.SUITABLE_DIFF));
      final CommittedChangeListKey key = new CommittedChangeListKey(list.getCommitterName(), roundedTime, list.getComment());
      final long number = numberCache.get(key);
      if (number == 0) {
        numberCache.put(key, lastNumber);
        return lastNumber++;
      }
      return number;
    }
  }

  private static class CommittedChangeListKey extends Trinity<String, Long, String> {

    CommittedChangeListKey(String name, Long commitDate, String comment) {
      super(name, commitDate, comment);
    }
  }

  @Override
  @Nullable
  public CvsRepositoryLocation getLocationFor(@NotNull FilePath root) {
    if (!CvsUtil.fileIsUnderCvs(root.getIOFile())) {
      return null;
    }
    final VirtualFile rootDir = root.isDirectory() ? root.getVirtualFile() : root.getVirtualFileParent();
    final String module = CvsUtil.getModuleName(root);
    final CvsEnvironment connectionSettings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(rootDir);
    return new CvsRepositoryLocation(root.getVirtualFile(), connectionSettings, module);
  }

  @Override
  public ChangeListColumn @NotNull [] getColumns() {
    return new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME, ChangeListColumn.DESCRIPTION, BRANCH_COLUMN };
  }

  @Override
  public int getUnlimitedCountValue() {
    return 0;
  }

  @Nullable
  @Override
  public Pair<CvsChangeList, FilePath> getOneList(@NotNull VirtualFile file, VcsRevisionNumber number) throws VcsException {
    final File ioFile = new File(file.getPath());
    final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(ioFile);
    final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(filePath);
    final CvsRepositoryLocation cvsLocation = getLocationFor(filePath);
    if (cvsLocation == null) return null;
    final String module = CvsUtil.getModuleName(vcsRoot);
    final CvsEnvironment connectionSettings = cvsLocation.getEnvironment();
    if (connectionSettings.isOffline()) {
      return null;
    }
    final CvsChangeListsBuilder builder = new CvsChangeListsBuilder(module, connectionSettings, myProject, vcsRoot);

    final Ref<CvsChangeList> result = new Ref<>();
    final LoadHistoryOperation operation = new LoadHistoryOperation(connectionSettings, wrapper -> {
      final List<Revision> revisions = wrapper.getRevisions();
      if (revisions.isEmpty()) return;
      final RevisionWrapper revision = new RevisionWrapper(wrapper.getFile(), revisions.get(0), null);
      result.set(builder.addRevision(revision));
    }, cvsLocation.getModuleName(), number.asString());
    final CvsResult executionResult = operation.run(myProject);

    if (executionResult.isCanceled()) {
      throw new ProcessCanceledException();
    }
    else if (executionResult.hasErrors()) {
      throw executionResult.composeError();
    }
    if (result.isNull()) {
      return null;
    }
    final Date commitDate = result.get().getCommitDate();
    final CvsEnvironment rootConnectionSettings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(vcsRoot);
    final long t = commitDate.getTime();
    final Date dateFrom = new Date(t - CvsChangeList.SUITABLE_DIFF);
    final Date dateTo = new Date(t + CvsChangeList.SUITABLE_DIFF);

    final LoadHistoryOperation operation2 =
      new LoadHistoryOperation(rootConnectionSettings, module, dateFrom, dateTo, wrapper -> {
        final List<RevisionWrapper> wrappers = builder.revisionWrappersFromLog(wrapper);
        if (wrappers != null) {
          for (RevisionWrapper revisionWrapper : wrappers) {
            if (result.get().containsFileRevision(revisionWrapper)) {
              // otherwise a new change list will be created because the old change list already contains this file.
              continue;
            }
            builder.addRevision(revisionWrapper);
          }
        }
      });
    final CvsResult cvsResult = operation2.run(myProject);
    if (cvsResult.hasErrors()) {
      throw cvsResult.composeError();
    }
    return Pair.create(result.get(), filePath);
  }

  @NotNull
  @Override
  public List<CvsChangeList> getCommittedChanges(ChangeBrowserSettings settings, @NotNull RepositoryLocation location, int maxCount)
    throws VcsException {
    final CvsRepositoryLocation cvsLocation = (CvsRepositoryLocation)location;
    final String module = cvsLocation.getModuleName();
    final VirtualFile rootFile = cvsLocation.getRootFile();
    return loadCommittedChanges(settings, module, cvsLocation.getEnvironment(), rootFile);
  }

  @Override
  public void loadCommittedChanges(ChangeBrowserSettings settings,
                                   @NotNull RepositoryLocation location,
                                   int maxCount,
                                   @NotNull AsynchConsumer<? super CommittedChangeList> consumer) throws VcsException {
    try {
      final CvsRepositoryLocation cvsLocation = (CvsRepositoryLocation)location;
      final String module = cvsLocation.getModuleName();
      final CvsEnvironment connectionSettings = cvsLocation.getEnvironment();
      if (connectionSettings.isOffline()) {
        return;
      }
      final CvsChangeListsBuilder builder = new CvsChangeListsBuilder(module, connectionSettings, myProject, cvsLocation.getRootFile());
      final Date dateTo = settings.getDateBeforeFilter();
      Date dateFrom = settings.getDateAfterFilter();
      if (dateFrom == null) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(1970, Calendar.MARCH, 2);
        dateFrom = calendar.getTime();
      }
      final ChangeBrowserSettings.Filter filter = settings.createFilter();
      final Set<CvsChangeList> controlSet = new HashSet<>();
      final LoadHistoryOperation operation =
        new LoadHistoryOperation(connectionSettings, module, dateFrom, dateTo, wrapper -> {
          final List<RevisionWrapper> wrappers = builder.revisionWrappersFromLog(wrapper);
          if (wrappers != null) {
            for (RevisionWrapper revisionWrapper : wrappers) {
              final CvsChangeList changeList = builder.addRevision(revisionWrapper);
              if (controlSet.contains(changeList)) continue;
              controlSet.add(changeList);
              if (filter.accepts(changeList)) {
                consumer.consume(changeList);
              }
            }
          }
        });
      final CvsResult executionResult = operation.run(myProject);

      if (executionResult.isCanceled()) {
        throw new ProcessCanceledException();
      }
      else if (executionResult.hasErrors()) {
        throw executionResult.composeError();
      }
    }
    finally {
      consumer.finished();
    }
  }

  @NotNull
  private List<CvsChangeList> loadCommittedChanges(final ChangeBrowserSettings settings,
                                                   final String module,
                                                   CvsEnvironment connectionSettings,
                                                   final VirtualFile rootFile) throws VcsException {
    if (connectionSettings.isOffline()) {
      return Collections.emptyList();
    }
    final CvsChangeListsBuilder builder = new CvsChangeListsBuilder(module, connectionSettings, myProject, rootFile);
    final Date dateTo = settings.getDateBeforeFilter();
    Date dateFrom = settings.getDateAfterFilter();
    if (dateFrom == null) {
      final Calendar calendar = Calendar.getInstance();
      calendar.set(1970, Calendar.MARCH, 2);
      dateFrom = calendar.getTime();
    }
    final LoadHistoryOperation operation =
      new LoadHistoryOperation(connectionSettings, module, dateFrom, dateTo, logInformationWrapper -> builder.add(logInformationWrapper));
    final CvsResult executionResult = operation.run(myProject);

    if (executionResult.isCanceled()) {
      throw new ProcessCanceledException();
    }
    else if (executionResult.hasErrors()) {
      throw executionResult.composeError();
    }
    else {
      final List<CvsChangeList> versions = builder.getVersions();
      settings.filterChanges(versions);
      return versions;
    }
  }

  @Override
  public int getFormatVersion() {
    return 3;
  }

  @Override
  public void writeChangeList(@NotNull DataOutput stream, @NotNull CvsChangeList list) throws IOException {
    list.writeToStream(stream);
  }

  @NotNull
  @Override
  public CvsChangeList readChangeList(@NotNull RepositoryLocation location, @NotNull DataInput stream) throws IOException {
    final CvsRepositoryLocation cvsLocation = (CvsRepositoryLocation)location;
    return new CvsChangeList(myProject, cvsLocation.getEnvironment(), cvsLocation.getRootFile(), stream);
  }

  @Override
  public boolean isMaxCountSupported() {
    return false;
  }

  @Override
  public boolean refreshCacheByNumber() {
    return false;
  }

  @Override
  public String getChangelistTitle() {
    return null;
  }

  @Override
  public boolean isChangeLocallyAvailable(@NotNull FilePath filePath,
                                          @Nullable VcsRevisionNumber localRevision,
                                          @NotNull VcsRevisionNumber changeRevision,
                                          @NotNull CvsChangeList changeList) {
    if (localRevision instanceof CvsRevisionNumber && changeRevision instanceof CvsRevisionNumber) {
      final CvsRevisionNumber cvsLocalRevision = (CvsRevisionNumber)localRevision;
      final CvsRevisionNumber cvsChangeRevision = (CvsRevisionNumber)changeRevision;
      final int[] localSubRevisions = cvsLocalRevision.getSubRevisions();
      final int[] changeSubRevisions = cvsChangeRevision.getSubRevisions();
      if (localSubRevisions != null && changeSubRevisions != null) {
        if (localSubRevisions.length != changeSubRevisions.length) {
          // local is trunk, change is branch / vice versa
          return true;
        }
        for(int i=2; i<localSubRevisions.length; i += 2) {
          if (localSubRevisions [i] != changeSubRevisions [i]) {
            // local is one branch, change is a different branch
            return true;
          }
        }
      }
    }

    return isDifferentBranch(filePath, changeList) || (localRevision != null && localRevision.compareTo(changeRevision) >= 0);
  }

  private static boolean isDifferentBranch(@NotNull FilePath filePath, final CvsChangeList changeList) {
    final String localTag;
    final CvsEntriesManager cvsEntriesManager = CvsEntriesManager.getInstance();
    final VirtualFile parent = filePath.getVirtualFileParent();
    if (parent != null) {
      final Entry entry = cvsEntriesManager.getEntryFor(parent, filePath.getName());
      if (entry != null) {
        localTag = entry.getStickyTag();
      }
      else {
        localTag = getDirectoryTag(parent);
      }
    }
    else {
      final VirtualFile validParent = ChangesUtil.findValidParentAccurately(filePath);
      if (validParent == null) return false;
      localTag = getDirectoryTag(validParent);
    }
    final String remoteTag = changeList.getBranch();
    if (!Objects.equals(localTag, remoteTag)) {
      if (LOG.isDebugEnabled()) LOG.info(filePath + ": local tag " + localTag + ", remote tag " + remoteTag);
      return true;
    }
    return false;
  }

  @Nullable
  private static String getDirectoryTag(@NotNull final VirtualFile parent) {
    final String dirTag = CvsEntriesManager.getInstance().getCvsInfoFor(parent).getStickyTag();
    if (dirTag == null || !CvsUtil.isNonDateTag(dirTag)) return null;
    return dirTag.substring(1);
  }

  @Override
  public boolean refreshIncomingWithCommitted() {
    return true;
  }

  private final ChangeListColumn<CvsChangeList> BRANCH_COLUMN = new ChangeListColumn<CvsChangeList>() {
    @Override
    public String getTitle() {
      return CvsBundle.message("changelist.column.branch");
    }

    @Override
    public Object getValue(final CvsChangeList changeList) {
      final String branch = changeList.getBranch();
      return branch == null ? "HEAD" : branch;
    }
  };
}
