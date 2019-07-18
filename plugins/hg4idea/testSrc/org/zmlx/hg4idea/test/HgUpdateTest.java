// Copyright 2010 Victor Iacoban
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
package org.zmlx.hg4idea.test;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.zmlx.hg4idea.HgChange;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileStatusEnum;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.provider.update.HgRegularUpdater;
import org.zmlx.hg4idea.provider.update.HgUpdateConfigurationSettings;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@SuppressWarnings({"ConstantConditions"})
public class HgUpdateTest extends HgCollaborativeTest {

  private VirtualFile projectRepoVirtualFile;
  private File projectRepo;
  private File remoteRepo;

  @Override
  protected HgTestRepository initRepositories() throws Exception {
    myParentRepo = HgTestRepository.create(this);
    remoteRepo = new File(myParentRepo.getDirFixture().getTempDirPath());

    File aFile = fillFile(remoteRepo, new String[]{"com", "a.txt"}, "file contents");
    verify(runHg(remoteRepo, "add", aFile.getPath()));
    verify(runHg(remoteRepo, "status"), HgTestOutputParser.added("com", "a.txt"));
    verify(runHg(remoteRepo, "commit", "-m", "initial contents"));

    myRepo = myParentRepo.cloneRepository();
    projectRepo = new File(myRepo.getDirFixture().getTempDirPath());
    return myRepo;
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    projectRepoVirtualFile = myRepo.getDir();
  }

  @Test
  public void updateKeepsWorkingAfterPull() throws Exception {

    changeFile_A_AndCommitInRemoteRepository();

    //do a simple pull without an update
    HgPullCommand pull = new HgPullCommand(myProject, projectRepoVirtualFile);
    pull.setSource(HgUtil.getRepositoryDefaultPath(myProject, projectRepoVirtualFile));
    pull.executeInCurrentThread();

    assertEquals("The update operation should have pulled the incoming changes from the default repository.", 0,
                 determineNumberOfIncomingChanges(projectRepo));

    updateThroughPlugin();

    HgRevisionNumber parentRevision = new HgWorkingCopyRevisionsCommand(myProject).firstParent(projectRepoVirtualFile);
    assertEquals("The working directory should have been updated to the latest version", "1", parentRevision.getRevision());
  }

  @Test
  public void updateShouldMergeAndCommitChanges() throws Exception{
    changeFile_A_AndCommitInRemoteRepository();
    createAndCommitNewFileInLocalRepository();
    //we've got diverging heads in remote and local now

    PreUpdateInformation preUpdateInformation = new PreUpdateInformation().getPreUpdateInformation();
    HgRevisionNumber incomingHead = preUpdateInformation.getIncomingHead();
    HgRevisionNumber headBeforeUpdate = preUpdateInformation.getHeadBeforeUpdate();

    List<VcsException> warnings = updateThroughPlugin();

    assertEquals("Plain update should not generate warnings", 0, warnings.size());

    assertCurrentHeadIsMerge(incomingHead, headBeforeUpdate);
  }
  
  @Test
  public void updateShouldNotMergeIfMultipleHeadsBeforeUpdate() throws Exception {
    changeFile_A_AndCommitInRemoteRepository();
    createAndCommitNewFileInLocalRepository();

    //create multiple heads locally
    HgUpdateCommand updateCommand = new HgUpdateCommand(myProject, projectRepoVirtualFile);
    HgRevisionNumber parent = new HgParentsCommand(myProject).executeInCurrentThread(projectRepoVirtualFile).get(0);
    HgParentsCommand parentsCommand = new HgParentsCommand(myProject);
    parentsCommand.setRevision(parent);
    List<HgRevisionNumber> parents = parentsCommand.executeInCurrentThread(projectRepoVirtualFile);
    updateCommand.setRevision(parents.get(0).getChangeset());
    updateCommand.execute();

    createFileInCommand(projectRepoVirtualFile.findChild("com"),"c.txt", "updated content");
    runHg(projectRepo, "commit", "-m", "creating new local head");

    List<HgRevisionNumber> branchHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).executeInCurrentThread();
    assertEquals(2, branchHeads.size());

    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(myProject).identify(projectRepoVirtualFile).getFirst();
    assertUpdateThroughPluginFails();
    HgRevisionNumber parentAfterUpdate = new HgWorkingCopyRevisionsCommand(myProject).identify(projectRepoVirtualFile).getFirst();
    List<HgRevisionNumber> branchHeadsAfterUpdate = new HgHeadsCommand(myProject, projectRepoVirtualFile).executeInCurrentThread();

    assertEquals(3, branchHeadsAfterUpdate.size());
    assertEquals(parentAfterUpdate, parentBeforeUpdate);
  }
  
  @Test
  public void localChangesShouldBeAllowedWithFastForwardUpdate() throws Exception{
    createFileInCommand(projectRepoVirtualFile.findChild("com"), "b.txt", "other file");
    runHg(projectRepo, "commit", "-m", "adding second file");
    runHg(projectRepo, "push");

    runHg(remoteRepo, "update");
    changeFile_A_AndCommitInRemoteRepository();

    fillFile(projectRepo, new String[]{"com", "b.txt"}, "local change");
    createFileInCommand(projectRepoVirtualFile.findChild("com"), "c.txt", "other file");


    assertIsChanged(HgFileStatusEnum.MODIFIED, "com", "b.txt");
    assertIsChanged(HgFileStatusEnum.ADDED, "com", "c.txt");

    PreUpdateInformation information = new PreUpdateInformation().getPreUpdateInformation();
    HgRevisionNumber incomingHead = information.getIncomingHead();
    
    List<VcsException> nonFatalWarnings = updateThroughPlugin();

    assertTrue(nonFatalWarnings.isEmpty());
    HgRevisionNumber parentAfterUpdate = new HgParentsCommand(myProject).executeInCurrentThread(projectRepoVirtualFile).get(0);
    assertEquals(parentAfterUpdate, incomingHead);

    assertIsChanged(HgFileStatusEnum.MODIFIED, "com", "b.txt");
    assertIsChanged(HgFileStatusEnum.ADDED, "com", "c.txt");
  }

  @Test
  public void updateShouldNotMergeTwoHeadsComingFromRemote() throws Exception {
    String originalParent = runHg(remoteRepo, "parents", "--template", "{rev}\n").getStdout().trim();
    changeFile_A_AndCommitInRemoteRepository();

    runHg(remoteRepo, "update", "--clean", originalParent);

    File file = fillFile(remoteRepo, new String[]{"com", "b.txt"}, "second file");
    runHg(remoteRepo, "add", file.getPath());
    runHg(remoteRepo, "commit", "-m", "adding second file");

    assertUpdateThroughPluginFails();

    List<HgRevisionNumber> branchHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).executeInCurrentThread();
    assertEquals(2, branchHeads.size());
  }

  private void assertIsChanged(HgFileStatusEnum status, String... filepath) {
    Set<HgChange> localChanges = new HgStatusCommand.Builder(true).build(myProject).executeInCurrentThread(projectRepoVirtualFile);
    assertTrue(localChanges.contains(new HgChange(getHgFile(filepath), status)));
  }

  @Override
  protected HgFile getHgFile(String... filepath) {
    File fileToInclude = projectRepo;
    for (String path : filepath) {
      fileToInclude = new File(fileToInclude, path);
    }
    return new HgFile(projectRepoVirtualFile, fileToInclude);
  }

  private void assertCurrentHeadIsMerge(HgRevisionNumber incomingHead, HgRevisionNumber headBeforeUpdate) {
    List<HgRevisionNumber> newHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).executeInCurrentThread();
    assertEquals("After updating, there should be only one head because the remote heads should have been merged", 1,
                 newHeads.size());
    HgRevisionNumber newHead = newHeads.get(0);
    HgParentsCommand parents = new HgParentsCommand(myProject);
    parents.setRevision(newHead);
    List<HgRevisionNumber> parentRevisions = parents.executeInCurrentThread(projectRepoVirtualFile);
    assertEquals(2, parentRevisions.size());
    assertTrue(parentRevisions.contains(incomingHead));
    assertTrue(parentRevisions.contains(headBeforeUpdate));
  }

  @Test
  public void updateShouldMergeButNotCommitWithConflicts() throws Exception{
    changeFile_A_AndCommitInRemoteRepository();

    VirtualFile commonFile = projectRepoVirtualFile.findChild("com").findChild("a.txt");
    assertNotNull(commonFile);

    VcsTestUtil.editFileInCommand(myProject, commonFile, "conflicting content");
    runHg(projectRepo, "commit", "-m", "adding conflicting history to local repository");
    
    PreUpdateInformation preUpdateInformation = new PreUpdateInformation().getPreUpdateInformation();
    HgRevisionNumber incomingHead = preUpdateInformation.getIncomingHead();
    HgRevisionNumber headBeforeUpdate = preUpdateInformation.getHeadBeforeUpdate();

    List<VcsException> warnings = updateThroughPlugin();
    assertFalse(warnings.isEmpty());
    assertTrue(warnings.get(warnings.size() - 1).getMessage().contains("conflicts"));
    assertTrue(warnings.get(warnings.size() - 1).getMessage().contains("commit"));

    List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(projectRepoVirtualFile);
    assertEquals(2, parents.size());
    assertTrue(parents.contains(incomingHead));
    assertTrue(parents.contains(headBeforeUpdate));
  }

  @Test
  public void updateShouldNotMergeWithNonCommittedChanges() throws Exception {
    changeFile_A_AndCommitInRemoteRepository();

    //generate some extra local history
    createAndCommitNewFileInLocalRepository();

    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(myProject).parents(projectRepoVirtualFile).get(0);

    VcsTestUtil.editFileInCommand(myProject, projectRepoVirtualFile.findFileByRelativePath("com/a.txt"), "modified file contents");

    assertUpdateThroughPluginFails();

    assertEquals("Remote head should have been pulled in", 2,
                 new HgHeadsCommand(myProject, projectRepoVirtualFile).executeInCurrentThread().size());

    assertEquals("No merge should have been attempted", 1,
                 new HgWorkingCopyRevisionsCommand(myProject).parents(projectRepoVirtualFile).size());

    assertEquals("No merge should have been attempted", parentBeforeUpdate,
                 new HgWorkingCopyRevisionsCommand(myProject).parents(projectRepoVirtualFile).get(0));
  }

  private void assertUpdateThroughPluginFails() {
    try {
      updateThroughPlugin();
      fail("The update should have failed because a merge cannot be initiated with outstanding changes");
    } catch (VcsException e) {
      //expected
    }
  }

  private void createAndCommitNewFileInLocalRepository() throws IOException {
    createFileInCommand(projectRepoVirtualFile.findChild("com"), "b.txt", "other file");
    runHg(projectRepo, "commit", "-m", "adding non-conflicting history to local repository");
  }

  private List<VcsException> updateThroughPlugin() throws VcsException {
    HgRegularUpdater updater = new HgRegularUpdater(myProject, projectRepoVirtualFile, new HgUpdateConfigurationSettings());
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    ArrayList<VcsException> nonFatalWarnings = new ArrayList<>();
    updater.update(updatedFiles, indicator, nonFatalWarnings);
    return nonFatalWarnings;
  }

  private void changeFile_A_AndCommitInRemoteRepository() throws IOException {
    fillFile(remoteRepo, new String[]{"com", "a.txt"}, "update file contents");
    runHg(remoteRepo, "commit", "-m", "Adding history to remote repository");

    assertEquals("The remote repository should have gotten new history", 1, determineNumberOfIncomingChanges(projectRepo));
  }

  private int determineNumberOfIncomingChanges(File repo) throws IOException {
    ProcessOutput result = runHg(repo, "-q", "incoming", "--template", "{rev} ");
    String output = result.getStdout();
    return output.length() == 0 ? 0 : output.split(" ").length;
  }

  private class PreUpdateInformation {
    private HgRevisionNumber incomingHead;
    private HgRevisionNumber headBeforeUpdate;

    public HgRevisionNumber getIncomingHead() {
      return incomingHead;
    }

    public HgRevisionNumber getHeadBeforeUpdate() {
      return headBeforeUpdate;
    }

    public PreUpdateInformation getPreUpdateInformation() {
      List<HgRevisionNumber> currentHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).executeInCurrentThread();
      List<HgRevisionNumber> incomingChangesets = new HgIncomingCommand(myProject).executeInCurrentThread(projectRepoVirtualFile);

      assertEquals(1, currentHeads.size());
      assertEquals(1, incomingChangesets.size());

      incomingHead = incomingChangesets.get(0);
      headBeforeUpdate = currentHeads.get(0);
      return this;
    }
  }
}

