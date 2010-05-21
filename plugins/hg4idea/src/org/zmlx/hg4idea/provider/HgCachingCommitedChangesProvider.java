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
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
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
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.command.HgShowConfigCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class HgCachingCommitedChangesProvider
  implements CachingCommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {

  private static final int DEFAULT_MAX_COUNT = 500;

  private final Project project;

  public HgCachingCommitedChangesProvider(Project project) {
    this.project = project;
  }

  public int getFormatVersion() {
    return 0;
  }

  public void writeChangeList(DataOutput dataOutput, CommittedChangeList committedChangeList)
    throws IOException {
    HgCommitedChangeList changeList = (HgCommitedChangeList) committedChangeList;
    writeRevisionNumber(dataOutput, changeList.getRevisionNumber());
    dataOutput.writeUTF(changeList.getCommitterName());
    dataOutput.writeUTF(changeList.getComment());
    dataOutput.writeLong(changeList.getCommitDate().getTime());
    dataOutput.writeInt(changeList.getChanges().size());
    for (Change change : changeList.getChanges()) {
      writeRevision(dataOutput, (HgContentRevision) change.getBeforeRevision());
      writeRevision(dataOutput, (HgContentRevision) change.getAfterRevision());
    }
  }

  public CommittedChangeList readChangeList(RepositoryLocation repositoryLocation,
    DataInput dataInput) throws IOException {
    HgRevisionNumber revisionNumber = readRevisionNumber(dataInput);
    String committerName = dataInput.readUTF();
    String comment = dataInput.readUTF();
    Date commitDate = new Date(dataInput.readLong());
    int changesCount = dataInput.readInt();
    List<Change> changes = new LinkedList<Change>();
    for (int i = 0; i < changesCount; i++) {
      HgContentRevision beforeRevision = readRevision(repositoryLocation, dataInput);
      HgContentRevision afterRevision = readRevision(repositoryLocation, dataInput);
      Change change = new Change(beforeRevision, afterRevision);
      changes.add(change);
    }
    return new HgCommitedChangeList(comment, committerName, revisionNumber, commitDate, changes);
  }

  private void writeRevision(DataOutput dataOutput, HgContentRevision revision) throws IOException {
    if (revision == null) {
      dataOutput.writeUTF("");
      dataOutput.writeUTF("");
      dataOutput.writeUTF("");
    } else {
      dataOutput.writeUTF(revision.getFile().getIOFile().toString());
      writeRevisionNumber(dataOutput, revision.getRevisionNumber());
    }
  }

  private HgContentRevision readRevision(RepositoryLocation repositoryLocation, DataInput dataInput)
    throws IOException {
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

  private void writeRevisionNumber(DataOutput dataOutput, HgRevisionNumber revisionNumber)
    throws IOException {
    dataOutput.writeUTF(revisionNumber.getRevision());
    dataOutput.writeUTF(revisionNumber.getChangeset());
  }

  private HgRevisionNumber readRevisionNumber(DataInput dataInput) throws IOException {
    String revisionRevision = dataInput.readUTF();
    String revisionChangeset = dataInput.readUTF();
    return HgRevisionNumber.getInstance(revisionRevision, revisionChangeset);
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
    HgRevisionNumber hgLocalRevision = (HgRevisionNumber) localRevision;
    HgRevisionNumber hgChangeRevision = (HgRevisionNumber) changeRevision;

    return hgLocalRevision != null && hgChangeRevision != null
      && hgLocalRevision.getRevisionAsInt() >= hgChangeRevision.getRevisionAsInt();
  }

  public boolean refreshIncomingWithCommitted() {
    return true;
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
    HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    String defaultPath = configCommand.getDefaultPath(repo);
    VirtualFile baseDir = project.getBaseDir();
    if (StringUtils.isBlank(defaultPath) && baseDir != null) {
      try {
        defaultPath = new File(baseDir.getPath()).toURI().toURL().toString();
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    return new HgRepositoryLocation(defaultPath, repo);
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

    List<HgFileRevision> localRevisions =
      hgLogCommand.execute(hgFile, maxCount > 0 ? maxCount : DEFAULT_MAX_COUNT);

    for (HgFileRevision revision : localRevisions) {
      HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
      List<Change> changes = new LinkedList<Change>();
      for (String file : revision.getFilesModified()) {
        changes.add(createChange(root, vcsRevisionNumber, file, file));
      }
      for (String file : revision.getFilesAdded()) {
        changes.add(createChange(root, vcsRevisionNumber, null, file));
      }
      for (String file : revision.getFilesDeleted()) {
        changes.add(createChange(root, vcsRevisionNumber, file, null));
      }
      for (Map.Entry<String, String> copiedFile : revision.getFilesCopied().entrySet()) {
        changes.add(
          createChange(root, vcsRevisionNumber, copiedFile.getKey(), copiedFile.getValue())
        );
      }
      result.add(
        new HgCommitedChangeList(
          revision.getCommitMessage(),
          revision.getAuthor(),
          vcsRevisionNumber,
          revision.getRevisionDate(),
          changes
        )
      );
    }
    return result;
  }

  private Change createChange(VirtualFile root, HgRevisionNumber vcsRevisionNumber,
    String fileBefore, String fileAfter) {
    HgContentRevision beforeRevision = fileBefore == null ? null : new HgContentRevision(
      project,
      new HgFile(root, new File(root.getPath(), fileBefore)),
      new HgWorkingCopyRevisionsCommand(project).parent(root, vcsRevisionNumber)
    );

    HgContentRevision afterRevision = fileAfter == null ? null : new HgContentRevision(
        project,
        new HgFile(root, new File(root.getPath(), fileAfter)),
        vcsRevisionNumber
      );

    return new Change(beforeRevision, afterRevision);
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
    return DEFAULT_MAX_COUNT;
  }

  public void loadCommittedChanges(ChangeBrowserSettings changeBrowserSettings,
    RepositoryLocation repositoryLocation, int i,
    AsynchConsumer<CommittedChangeList> committedChangeListAsynchConsumer) throws VcsException {
  }
}
