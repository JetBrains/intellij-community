// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.ui.HgVersionFilterComponent;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class HgCommittedChangesProvider implements CommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {

  private static final Logger LOG = Logger.getInstance(HgCommittedChangesProvider.class);
  private final Project project;
  private final HgVcs myVcs;

  public HgCommittedChangesProvider(Project project, HgVcs vcs) {
    this.project = project;
    myVcs = vcs;
  }

  @NotNull
  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new HgVersionFilterComponent(showDateFilter);
  }

  @Nullable
  public RepositoryLocation getLocationFor(FilePath filePath) {
    VirtualFile repo = VcsUtil.getVcsRootFor(project, filePath);
    if (repo == null) {
      return null;
    }
    return new HgRepositoryLocation(repo.getUrl(), repo);
  }

  public RepositoryLocation getLocationFor(FilePath root, String repositoryPath) {
    return getLocationFor(root);
  }

  @Nullable
  public VcsCommittedListsZipper getZipper() {
    return null;
  }

  @Override
  public void loadCommittedChanges(ChangeBrowserSettings changeBrowserSettings,
                                   RepositoryLocation repositoryLocation,
                                   int maxCount,
                                   final AsynchConsumer<CommittedChangeList> consumer) throws VcsException {

    try {
      List<CommittedChangeList> results = getCommittedChanges(changeBrowserSettings, repositoryLocation, maxCount);
      for (CommittedChangeList result : results) {
        consumer.consume(result);
      }
    }
    finally {
      consumer.finished();
    }
  }

  public List<CommittedChangeList> getCommittedChanges(ChangeBrowserSettings changeBrowserSettings,
                                                       RepositoryLocation repositoryLocation,
                                                       int maxCount) {
    VirtualFile root = ((HgRepositoryLocation)repositoryLocation).getRoot();

    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));

    List<CommittedChangeList> result = new LinkedList<>();
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);
    List<String> args = null;
    if (changeBrowserSettings != null) {
      HgLogArgsBuilder argsBuilder = new HgLogArgsBuilder(changeBrowserSettings);
      args = argsBuilder.getLogArgs();
      if (args.isEmpty()) {
        maxCount = maxCount == 0 ? VcsConfiguration.getInstance(project).MAXIMUM_HISTORY_ROWS  : maxCount;
      }
    }
    final List<HgFileRevision> localRevisions;
    localRevisions = hgLogCommand.execute(hgFile, maxCount == 0 ? -1 : maxCount, true, args);
    Collections.reverse(localRevisions);

    for (HgFileRevision revision : localRevisions) {
      HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
      List<HgRevisionNumber> parents = vcsRevisionNumber.getParents();

      HgRevisionNumber firstParent = parents.isEmpty() ? null : parents.get(0); // can have no parents if it is a root

      List<Change> changes = new ArrayList<>();
      for (String file : revision.getModifiedFiles()) {
        changes.add(createChange(root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
      }
      for (String file : revision.getAddedFiles()) {
        changes.add(createChange(root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
      }
      for (String file : revision.getDeletedFiles()) {
        changes.add(createChange(root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
      }
      for (Map.Entry<String, String> copiedFile : revision.getMovedFiles().entrySet()) {
        changes
          .add(createChange(root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber, HgChangeProvider.RENAMED));
      }

      result.add(new HgCommittedChangeList(myVcs, vcsRevisionNumber, revision.getBranchName(), revision.getCommitMessage(),
                                           revision.getAuthor(), revision.getRevisionDate(), changes));
    }
    Collections.reverse(result);
    return result;
  }

  private Change createChange(VirtualFile root,
                              @Nullable String fileBefore,
                              @Nullable HgRevisionNumber revisionBefore,
                              @Nullable String fileAfter,
                              HgRevisionNumber revisionAfter,
                              FileStatus aStatus) {

    HgContentRevision beforeRevision =
      fileBefore == null ? null : HgContentRevision.create(project, new HgFile(root, new File(root.getPath(), fileBefore)), revisionBefore);
    HgContentRevision afterRevision =
      fileAfter == null ? null : HgContentRevision.create(project, new HgFile(root, new File(root.getPath(), fileAfter)), revisionAfter);
    return new Change(beforeRevision, afterRevision, aStatus);
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[]{BRANCH_COLUMN, ChangeListColumn.NUMBER, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION, ChangeListColumn.NAME};
  }

  public VcsCommittedViewAuxiliary createActions(DecoratorManager decoratorManager, RepositoryLocation repositoryLocation) {
    AnAction copyHashAction = new AnAction("Copy &Hash", "Copy hash to clipboard", PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
        if (changeLists != null && changeLists[0] instanceof HgCommittedChangeList) {
          HgRevisionNumber revisionNumber = ((HgCommittedChangeList)changeLists[0]).getRevisionNumber();
          CopyPasteManager.getInstance().setContents(new StringSelection(revisionNumber.getChangeset()));
        }
      }
    };
    return new VcsCommittedViewAuxiliary(Collections.singletonList(copyHashAction), () -> {
    }, Collections.singletonList(copyHashAction));
  }

  public int getUnlimitedCountValue() {
    return -1;
  }

  @Override
  public Pair<CommittedChangeList, FilePath> getOneList(VirtualFile file, VcsRevisionNumber number) {
    final ChangeBrowserSettings settings = createDefaultSettings();
    settings.USE_CHANGE_AFTER_FILTER = true;
    settings.USE_CHANGE_BEFORE_FILTER = true;
    settings.CHANGE_AFTER = number.asString();
    settings.CHANGE_BEFORE = number.asString();
    // todo implement in proper way
    VirtualFile localVirtualFile = HgUtil.convertToLocalVirtualFile(file);
    if (localVirtualFile == null) {
      return null;
    }
    final FilePath filePath = VcsUtil.getFilePath(localVirtualFile);
    final CommittedChangeList list = getCommittedChangesForRevision(getLocationFor(filePath), number.asString());
    if (list != null) {
      return new Pair<>(list, filePath);
    }
    return null;
  }

  @Override
  public RepositoryLocation getForNonLocal(VirtualFile file) {
    return null;
  }

  @Override
  public boolean supportsIncomingChanges() {
    return false;
  }

  @Nullable
  public CommittedChangeList getCommittedChangesForRevision(@Nullable RepositoryLocation repositoryLocation, String revision) {
    if (repositoryLocation == null) {
      return null;
    }
    VirtualFile root = ((HgRepositoryLocation)repositoryLocation).getRoot();
    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);
    hgLogCommand.setFollowCopies(true);
    List<String> args = new ArrayList<>();
    args.add("--rev");
    args.add(revision);
    final List<HgFileRevision> revisions;
    revisions = hgLogCommand.execute(hgFile, 1, true, args);
    if (ContainerUtil.isEmpty(revisions)) {
      return null;
    }
    HgFileRevision localRevision = revisions.get(0);
    HgRevisionNumber vcsRevisionNumber = localRevision.getRevisionNumber();
    List<HgRevisionNumber> parents = vcsRevisionNumber.getParents();
    HgRevisionNumber firstParent = parents.isEmpty() ? null : parents.get(0); // can have no parents if it is a root
    List<Change> changes = new ArrayList<>();
    for (String file : localRevision.getModifiedFiles()) {
      changes.add(createChange(root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
    }
    for (String file : localRevision.getAddedFiles()) {
      changes.add(createChange(root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
    }
    for (String file : localRevision.getDeletedFiles()) {
      changes.add(createChange(root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
    }
    for (Map.Entry<String, String> copiedFile : localRevision.getMovedFiles().entrySet()) {
      changes.add(createChange(root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber, HgChangeProvider.RENAMED));
    }

    return new HgCommittedChangeList(myVcs, vcsRevisionNumber, localRevision.getBranchName(), localRevision.getCommitMessage(),
                                     localRevision.getAuthor(), localRevision.getRevisionDate(), changes);
  }

  private static final Comparator<HgCommittedChangeList> BRANCH_COLUMN_COMPARATOR =
    (o1, o2) -> Comparing.compare(o1.getBranch(), o2.getBranch());

  private static final ChangeListColumn<HgCommittedChangeList> BRANCH_COLUMN = new ChangeListColumn<HgCommittedChangeList>() {
    public String getTitle() {
      return HgVcsMessages.message("hg4idea.changelist.column.branch");
    }

    public Object getValue(final HgCommittedChangeList changeList) {
      final String branch = changeList.getBranch();
      return branch.isEmpty() ? "default" : branch;
    }

    @NotNull
    @Override
    public Comparator<HgCommittedChangeList> getComparator() {
      return BRANCH_COLUMN_COMPARATOR;
    }
  };

  private static class HgLogArgsBuilder {

    @NotNull private final ChangeBrowserSettings myBrowserSettings;

    HgLogArgsBuilder(@NotNull ChangeBrowserSettings browserSettings) {
      myBrowserSettings = browserSettings;
    }

    @NotNull
    List<String> getLogArgs() {

      StringBuilder args = new StringBuilder();
      Date afterDate = myBrowserSettings.getDateAfter();
      Date beforeDate = myBrowserSettings.getDateBefore();
      Long afterFilter = myBrowserSettings.getChangeAfterFilter();
      Long beforeFilter = myBrowserSettings.getChangeBeforeFilter();

      final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");

      if ((afterFilter != null) && (beforeFilter != null)) {
        args.append(afterFilter).append(":").append(beforeFilter);
      }
      else if (afterFilter != null) {
        args.append("tip:").append(afterFilter);
        if (afterFilter == 0) {
          LOG.debug("Get repository commits not from Log", new Throwable());
        }
      }
      else if (beforeFilter != null) {
        args.append("reverse(:").append(beforeFilter).append(")");
      }

      if (afterDate != null) {
        if (args.length() > 0) {
          args.append(" and ");
        }
        args.append("date('>").append(dateFormatter.format(afterDate)).append("')");
      }

      if (beforeDate != null) {
        if (args.length() > 0) {
          args.append(" and ");
        }

        args.append("date('<").append(dateFormatter.format(beforeDate)).append("')");
      }

      if (args.length() > 0) {
        List<String> logArgs = new ArrayList<>();
        logArgs.add("-r");
        logArgs.add(args.toString());
        return logArgs;
      }

      return Collections.emptyList();
    }
  }
}
