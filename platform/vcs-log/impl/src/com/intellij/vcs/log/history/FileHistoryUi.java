/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.history;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter;
import com.intellij.vcs.log.ui.highlighters.MyCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;

public class FileHistoryUi extends AbstractVcsLogUi {
  @NotNull private static final List<String> HIGHLIGHTERS = Arrays.asList(MyCommitsHighlighter.Factory.ID,
                                                                          CurrentBranchHighlighter.Factory.ID);
  @NotNull private final FileHistoryUiProperties myUiProperties;
  @NotNull private final FileHistoryFilterUi myFilterUi;
  @NotNull private final FilePath myPath;
  @NotNull private final FileHistoryPanel myFileHistoryPanel;
  @NotNull private final IndexDataGetter myIndexDataGetter;
  @NotNull private final MyPropertiesChangeListener myPropertiesChangeListener;

  public FileHistoryUi(@NotNull VcsLogData logData,
                       @NotNull Project project,
                       @NotNull VcsLogColorManager manager,
                       @NotNull FileHistoryUiProperties uiProperties,
                       @NotNull VisiblePackRefresher refresher,
                       @NotNull FilePath path) {
    super(logData, project, manager, refresher);
    myUiProperties = uiProperties;

    myIndexDataGetter = ObjectUtils.assertNotNull(logData.getIndex().getDataGetter());
    myFilterUi = new FileHistoryFilterUi(path, uiProperties);
    myPath = path;
    myFileHistoryPanel = new FileHistoryPanel(this, logData, myVisiblePack, path);

    updateFilter();

    for (VcsLogHighlighterFactory factory : ContainerUtil.filter(Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, myProject),
                                                                 f -> HIGHLIGHTERS.contains(f.getId()))) {
      getTable().addHighlighter(factory.createHighlighter(logData, this));
    }

    myPropertiesChangeListener = new MyPropertiesChangeListener();
    myUiProperties.addChangeListener(myPropertiesChangeListener);
  }

  @Nullable
  public VirtualFile createVcsVirtualFile(@NotNull VcsFullCommitDetails details) {
    VcsFileRevision revision = createRevision(details);
    return createVcsVirtualFile(revision);
  }

  @Nullable
  public VirtualFile createVcsVirtualFile(@Nullable VcsFileRevision revision) {
    if (!VcsHistoryUtil.isEmpty(revision)) {
      if (revision instanceof VcsFileRevisionEx) {
        FilePath path = ((VcsFileRevisionEx)revision).getPath();
        return path.isDirectory()
               ? new VcsVirtualFolder(path.getPath(), null, VcsFileSystem.getInstance())
               : new VcsVirtualFile(path.getPath(), revision, VcsFileSystem.getInstance());
      }
    }
    return null;
  }

  @Nullable
  public VcsFileRevision createRevision(@Nullable VcsFullCommitDetails details) {
    if (details != null && !(details instanceof LoadingDetails)) {
      List<Change> changes = collectRelevantChanges(details);
      for (Change change : changes) {
        ContentRevision revision = change.getAfterRevision();
        if (revision != null) {
          return new VcsLogFileRevision(details, revision, revision.getFile());
        }
      }
      if (!changes.isEmpty()) {
        // file was deleted
        return VcsFileRevision.NULL;
      }
    }
    return null;
  }

  @Nullable
  public FilePath getAfterPath(@NotNull VcsFullCommitDetails details) {
    if (myPath.isDirectory()) return myPath;

    List<Change> changes = collectRelevantChanges(details);
    for (Change change : changes) {
      ContentRevision revision = change.getAfterRevision();
      if (revision != null) {
        return revision.getFile();
      }
    }

    return null;// file was deleted
  }

  @Nullable
  public FilePath getBeforePath(@NotNull VcsFullCommitDetails details) {
    if (myPath.isDirectory()) return myPath;

    List<Change> changes = collectRelevantChanges(details);
    for (Change change : changes) {
      ContentRevision revision = change.getBeforeRevision();
      if (revision != null) {
        return revision.getFile();
      }
    }

    return null;// file was created
  }

  @NotNull
  public List<Change> collectRelevantChanges(@NotNull VcsFullCommitDetails details) {
    Set<FilePath> fileNames = getFileNames(details);
    if (myPath.isDirectory()) {
      return ContainerUtil.filter(details.getChanges(), change -> affectsDirectories(change, fileNames));
    }
    else {
      return ContainerUtil.filter(details.getChanges(), change -> affectsFiles(change, fileNames));
    }
  }

  @NotNull
  private Set<FilePath> getFileNames(@NotNull VcsFullCommitDetails details) {
    int commitIndex = myLogData.getStorage().getCommitIndex(details.getId(), details.getRoot());
    Set<FilePath> names;
    if (myVisiblePack instanceof FileHistoryVisiblePack) {
      Map<Integer, FilePath> namesData = ((FileHistoryVisiblePack)myVisiblePack).getNamesData();
      names = Collections.singleton(namesData.get(commitIndex));
    }
    else {
      names = myIndexDataGetter.getFileNames(myPath, commitIndex);
    }
    if (names.isEmpty()) return Collections.singleton(myPath);
    return names;
  }

  private static boolean affectsFiles(@NotNull Change change, @NotNull Set<FilePath> files) {
    ContentRevision revision = notNull(chooseNotNull(change.getAfterRevision(), change.getBeforeRevision()));
    return files.contains(revision.getFile());
  }

  private static boolean affectsDirectories(@NotNull Change change, @NotNull Set<FilePath> directories) {
    FilePath file = notNull(chooseNotNull(change.getAfterRevision(), change.getBeforeRevision())).getFile();
    return ContainerUtil.find(directories, dir -> VfsUtilCore.isAncestor(dir.getIOFile(), file.getIOFile(), false)) != null;
  }

  @NotNull
  public List<Change> collectChanges(@NotNull List<VcsFullCommitDetails> detailsList, boolean onlyRelevant) {
    List<Change> changes = ContainerUtil.newArrayList();
    List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
    for (VcsFullCommitDetails details : detailsListReversed) {
      changes.addAll(onlyRelevant ? collectRelevantChanges(details) : details.getChanges());
    }

    return CommittedChangesTreeBrowser.zipChanges(changes);
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId, @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter) {
    String mainText = "Commit " + commitId.toString() + " does not exist in history for " + myPath.getName();
    if (getFilters().getBranchFilter() != null) {
      showWarningWithLink(mainText + " in current branch.", "Show all branches and search again.", () -> {
        myUiProperties.set(FileHistoryUiProperties.SHOW_ALL_BRANCHES, true);
        invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create()));
      });
    }
    else {
      VcsLogUiImpl mainLogUi = VcsProjectLog.getInstance(myProject).getMainLogUi();
      if (mainLogUi != null) {
        showWarningWithLink(mainText + ".", "Search in Log.", () -> {
          if (VcsLogContentProvider.selectLogUi(myProject, mainLogUi)) {
            if (commitId instanceof Hash) {
              mainLogUi.jumpToCommit((Hash)commitId,
                                     notNull(VcsUtil.getVcsRootFor(myProject, myPath)),
                                     SettableFuture.create());
            }
            else if (commitId instanceof String) {
              mainLogUi.jumpToCommitByPartOfHash((String)commitId, SettableFuture.create());
            }
          }
        });
      }
      else {
        super.handleCommitNotFound(commitId, rowGetter);
      }
    }
  }

  @NotNull
  public FilePath getPath() {
    return myPath;
  }

  @NotNull
  @Override
  public VcsLogFilterUi getFilterUi() {
    return myFilterUi;
  }

  @Override
  public boolean areGraphActionsEnabled() {
    return false;
  }

  @Override
  public boolean isMultipleRoots() {
    return false;
  }

  @Override
  public boolean isShowRootNames() {
    return false;
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    return HIGHLIGHTERS.contains(id);
  }

  @Override
  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    myFileHistoryPanel.updateDataPack(myVisiblePack, permGraphChanged);
  }

  @NotNull
  @Override
  public VcsLogGraphTable getTable() {
    return myFileHistoryPanel.getGraphTable();
  }

  @NotNull
  @Override
  public Component getMainComponent() {
    return myFileHistoryPanel;
  }

  @Override
  protected VcsLogFilterCollection getFilters() {
    return myFilterUi.getFilters();
  }

  private void updateFilter() {
    myRefresher.onFiltersChange(myFilterUi.getFilters());
  }

  @NotNull
  public FileHistoryUiProperties getProperties() {
    return myUiProperties;
  }

  @Override
  public void dispose() {
    myUiProperties.removeChangeListener(myPropertiesChangeListener);
    super.dispose();
  }

  private class MyPropertiesChangeListener implements VcsLogUiProperties.PropertiesChangeListener {
    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
      if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
        myFileHistoryPanel.showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));
      }
      else if (FileHistoryUiProperties.SHOW_ALL_BRANCHES.equals(property)) {
        updateFilter();
      }
      else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
        getTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof CommonUiProperties.TableColumnProperty) {
        getTable().forceReLayout(((CommonUiProperties.TableColumnProperty)property).getColumn());
      }
    }
  }
}
