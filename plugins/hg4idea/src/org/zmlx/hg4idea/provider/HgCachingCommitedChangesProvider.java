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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgContentRevision;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgIncomingCommand;
import org.zmlx.hg4idea.command.HgLogCommand;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class HgCachingCommitedChangesProvider
  implements CachingCommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {

  private final Project project;

  public HgCachingCommitedChangesProvider(Project project) {
    this.project = project;
  }

  public int getFormatVersion() {
    return 0;
  }

  public CommittedChangeList readChangeList(RepositoryLocation repositoryLocation,
    DataInput dataInput) throws IOException {
    HgRevisionNumber revision = HgRevisionNumber.getInstance(
      dataInput.readUTF(),
      dataInput.readUTF()
    );
    String committerName = dataInput.readUTF();
    String comment = dataInput.readUTF();
    Date commitDate = new Date(dataInput.readLong());
    int changesCount = dataInput.readInt();
    List<Change> changes = new ArrayList<Change>();
    for (int i = 0; i < changesCount; i++) {
      HgContentRevision beforeRevision = readRevision(repositoryLocation, dataInput);
      HgContentRevision afterRevision = readRevision(repositoryLocation, dataInput);
      changes.add(new Change(beforeRevision, afterRevision));
    }
    return new HgCommitedChangeList(
      revision, comment,
      committerName,
      commitDate,
      changes);
  }

  public void writeChangeList(DataOutput dataOutput, CommittedChangeList committedChangeList)
    throws IOException {
    HgCommitedChangeList changeList = (HgCommitedChangeList) committedChangeList;
    writeRevisionNumber(dataOutput, changeList.getRevision());
    dataOutput.writeUTF(changeList.getCommitterName());
    dataOutput.writeUTF(changeList.getComment());
    dataOutput.writeLong(changeList.getCommitDate().getTime());
    dataOutput.writeInt(changeList.getChanges().size());
    for (Change change : changeList.getChanges()) {
      writeRevision(dataOutput, (HgContentRevision) change.getBeforeRevision());
      writeRevision(dataOutput, (HgContentRevision) change.getAfterRevision());
    }
  }

  private HgContentRevision readRevision(RepositoryLocation repositoryLocation, DataInput dataInput) throws IOException {
    String revisionPath = dataInput.readUTF();
    HgRevisionNumber revisionNumber = readRevisionNumber(dataInput);

    if (!StringUtils.isEmpty(revisionPath)) {
      VirtualFile root = ((HgRepositoryLocation) repositoryLocation).getRoot();
      return new HgContentRevision(
        project,
        new HgFile(root, new File(revisionPath)),
        revisionNumber
      );
    } else {
      return null;
    }
  }

  private void writeRevision(DataOutput dataOutput, HgContentRevision revision) throws IOException {
    if (revision == null) {
      dataOutput.writeUTF("");
      writeRevisionNumber(dataOutput, HgRevisionNumber.getInstance("", ""));
    } else {
      dataOutput.writeUTF(revision.getFile().getIOFile().toString());
      writeRevisionNumber(dataOutput, revision.getRevisionNumber());
    }
  }

  private HgRevisionNumber readRevisionNumber(DataInput dataInput) throws IOException {
    String revisionRevision = dataInput.readUTF();
    String revisionChangeset = dataInput.readUTF();
    return HgRevisionNumber.getInstance(revisionRevision, revisionChangeset);
  }

  private void writeRevisionNumber(DataOutput dataOutput, HgRevisionNumber revisionNumber) throws IOException {
    dataOutput.writeUTF(revisionNumber.getRevision());
    dataOutput.writeUTF(revisionNumber.getChangeset());
  }

  public boolean isMaxCountSupported() {
    return true;
  }

  public Collection<FilePath> getIncomingFiles(RepositoryLocation repositoryLocation)
    throws VcsException {
    return null;
  }

  public boolean refreshCacheByNumber() {
    return false;
  }

  @Nls
  public String getChangelistTitle() {
    return null;
  }

  public boolean isChangeLocallyAvailable(FilePath filePath,
    @Nullable VcsRevisionNumber localRevision,
    VcsRevisionNumber changeRevision, CommittedChangeList committedChangeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  public boolean refreshIncomingWithCommitted() {
    return false;
  }

  public void loadCommittedChanges(ChangeBrowserSettings changeBrowserSettings, RepositoryLocation repositoryLocation, int i, AsynchConsumer<CommittedChangeList> committedChangeListAsynchConsumer) throws VcsException {
    throw new UnsupportedOperationException();  //TODO implement method
  }

  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean b) {
    return null;
  }

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

  public VcsCommittedListsZipper getZipper() {
    return null;
  }

  public List<CommittedChangeList> getCommittedChanges(ChangeBrowserSettings changeBrowserSettings,
    RepositoryLocation repositoryLocation, int maxCount) throws VcsException {
    VirtualFile root = ((HgRepositoryLocation) repositoryLocation).getRoot();

    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));

    List<CommittedChangeList> result = new LinkedList<CommittedChangeList>();
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);

    List<HgFileRevision> localRevisions = hgLogCommand.execute(hgFile, maxCount == 0 ? -1 : maxCount, true); //can be zero
    List<HgRevisionNumber> incomingRevisions = new HgIncomingCommand(project).execute(root);

    Collections.reverse(localRevisions);

    for (HgFileRevision revision : localRevisions) {
      HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
      HgRevisionNumber firstParent = vcsRevisionNumber.getParents().get(0);
      List<Change> changes = new ArrayList<Change>();
      for (String file : revision.getModifiedFiles()) {
        changes.add(createChange(root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
      }
      for (String file : revision.getAddedFiles()) {
        changes.add(createChange(root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
      }
      for (String file : revision.getDeletedFiles()) {
        changes.add(createChange(root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
      }
      for (Map.Entry<String,String> copiedFile : revision.getCopiedFiles().entrySet()) {
        changes.add(createChange(root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber, FileStatus.ADDED));
      }

      result.add(new HgCommitedChangeList(
        vcsRevisionNumber, revision.getCommitMessage(),
        revision.getAuthor(),
        revision.getRevisionDate(),
        changes));

    }
    Collections.reverse(result);
    return result;
  }

  private Change createChange(VirtualFile root,
    String fileBefore,
    HgRevisionNumber revisionBefore,
    String fileAfter,
    HgRevisionNumber revisionAfter,
    FileStatus aStatus ) {
    
    HgContentRevision beforeRevision = fileBefore == null ? null : new HgContentRevision(
      project,
      new HgFile(root, new File(root.getPath(), fileBefore)),
      revisionBefore
    );
    HgContentRevision afterRevision = fileAfter == null ? null : new HgContentRevision(
      project,
      new HgFile(root, new File(root.getPath(), fileAfter)),
      revisionAfter
    );
    return new Change(
      beforeRevision,
      afterRevision,
      aStatus
    );
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] {
      ChangeListColumn.NUMBER,
      ChangeListColumn.DATE,
      ChangeListColumn.DESCRIPTION,
      ChangeListColumn.NAME
    };
  }

  public VcsCommittedViewAuxiliary createActions(DecoratorManager decoratorManager,
    RepositoryLocation repositoryLocation) {
    return null;
  }

  public int getUnlimitedCountValue() {
    return -1;
  }

  @Override
  public CommittedChangeList getOneList(RepositoryLocation location, VcsRevisionNumber number) throws VcsException {
    final ChangeBrowserSettings settings = createDefaultSettings();
    settings.USE_CHANGE_AFTER_FILTER = true;
    settings.USE_CHANGE_BEFORE_FILTER = true;
    settings.CHANGE_AFTER = number.asString();
    settings.CHANGE_BEFORE = number.asString();
    final List<CommittedChangeList> list = getCommittedChanges(settings, location, 1);
    if (list.size() == 1) {
      return list.get(0);
    }
    return null;
  }
}
