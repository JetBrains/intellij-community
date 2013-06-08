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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.zmlx.hg4idea.HgChange;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileStatusEnum;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.provider.update.HgRegularUpdater;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.*;

@SuppressWarnings({"ConstantConditions", "ThrowableResultOfMethodCallIgnored"})
public class HgUpdateTest extends HgCollaborativeTest {

  private VirtualFile projectRepoVirtualFile;
  private VirtualFile remoteRepoVirtualFile;
  private File projectRepo;
  private File remoteRepo;

  @BeforeMethod
  @Override
  protected void setUp(Method testMethod) throws Exception {
    super.setUp(testMethod);
    projectRepoVirtualFile = myRepo.getDir();
    remoteRepoVirtualFile = myParentRepo.getDir();
    projectRepo = new File(myRepo.getDir().getPath());
    remoteRepo = new File(myParentRepo.getDir().getPath());
  }

  @Test
  public void updateKeepsWorkingAfterPull() throws Exception {

    changeFile_A_AndCommitInRemoteRepository();

    //do a simple pull without an update
    HgPullCommand pull = new HgPullCommand(myProject, projectRepoVirtualFile);
    pull.setSource(new HgShowConfigCommand(myProject).getDefaultPath(projectRepoVirtualFile));
    pull.setUpdate(false);
    pull.execute();

    assertEquals( determineNumberOfIncomingChanges( projectRepo ), 0,
                  "The update operation should have pulled the incoming changes from the default repository." );
    
    updateThroughPlugin();

    HgRevisionNumber parentRevision = new HgWorkingCopyRevisionsCommand(myProject).firstParent(projectRepoVirtualFile);
    assertEquals( parentRevision.getRevision(), "1",
                  "The working directory should have been updated to the latest version" );

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

    assertEquals(warnings.size(), 0, "Plain update should not generate warnings");

    assertCurrentHeadIsMerge(incomingHead, headBeforeUpdate);
  }
  
  @Test
  public void updateShouldNotMergeIfMultipleHeadsBeforeUpdate() throws Exception {
    changeFile_A_AndCommitInRemoteRepository();
    createAndCommitNewFileInLocalRepository();

    //create multiple heads locally
    HgUpdateCommand updateCommand = new HgUpdateCommand(myProject, projectRepoVirtualFile);
    HgRevisionNumber parent = new HgParentsCommand(myProject).execute(projectRepoVirtualFile).get(0);
    HgParentsCommand parentsCommand = new HgParentsCommand(myProject);
    parentsCommand.setRevision(parent);
    List<HgRevisionNumber> parents = parentsCommand.execute(projectRepoVirtualFile);
    updateCommand.setRevision(parents.get(0).getChangeset());
    updateCommand.execute();

    createFileInCommand(projectRepoVirtualFile.findChild("com"),"c.txt", "updated content");
    runHg(projectRepo, "commit", "-m", "creating new local head");

    List<HgRevisionNumber> branchHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).execute();
    assertEquals(branchHeads.size(), 2);

    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(myProject).identify(projectRepoVirtualFile).getFirst();
    assertUpdateThroughPluginFails();
    HgRevisionNumber parentAfterUpdate = new HgWorkingCopyRevisionsCommand(myProject).identify(projectRepoVirtualFile).getFirst();
    List<HgRevisionNumber> branchHeadsAfterUpdate = new HgHeadsCommand(myProject, projectRepoVirtualFile).execute();

    assertEquals(branchHeadsAfterUpdate.size(), 3);
    assertEquals(parentBeforeUpdate, parentAfterUpdate);
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
    HgRevisionNumber parentAfterUpdate = new HgParentsCommand(myProject).execute(projectRepoVirtualFile).get(0);
    assertEquals(incomingHead, parentAfterUpdate);

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

    List<HgRevisionNumber> branchHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).execute();
    assertEquals(branchHeads.size(), 2);


  }

  private void assertIsChanged(HgFileStatusEnum status, String... filepath) {
    Set<HgChange> localChanges = new HgStatusCommand.Builder(true).build(myProject).execute(projectRepoVirtualFile);
    assertTrue(localChanges.contains(new HgChange(getHgFile(filepath), status)));
  }

  @Override
  protected HgFile getHgFile(String... filepath) {
    File fileToInclude = projectRepo;
    for (int i = 0; i < filepath.length; i++) {
      fileToInclude = new File(fileToInclude, filepath[i]);
    }
    return new HgFile(projectRepoVirtualFile, fileToInclude);
  }

  private void assertCurrentHeadIsMerge(HgRevisionNumber incomingHead, HgRevisionNumber headBeforeUpdate) {
    List<HgRevisionNumber> newHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).execute();
    assertEquals(newHeads.size(), 1, "After updating, there should be only one head because the remote heads should have been merged");
    HgRevisionNumber newHead = newHeads.get(0);
    HgParentsCommand parents = new HgParentsCommand(myProject);
    parents.setRevision(newHead);
    List<HgRevisionNumber> parentRevisions = parents.execute(projectRepoVirtualFile);
    assertEquals(parentRevisions.size(), 2);
    assertTrue(parentRevisions.contains(incomingHead));
    assertTrue(parentRevisions.contains(headBeforeUpdate));
  }

  @Test
  public void updateShouldMergeButNotCommitWithConflicts() throws Exception{

    // Puts a single file into the remote repo. After this, the remote repo has history like:
    //  0:43a0c011b027
    changeFile_A_AndCommitInRemoteRepository();

    // Now update the project repo from the remote. Otherwise, there won't be any commits in the project
    //  repo, and so hg will claim the two to be unrelated. See http://stackoverflow.com/questions/7313178/mercurial-error-repository-is-unrelated.
    //  After this, the project repo will have a history that is identical to the remote repo:
    //  0:43a0c011b027
    runHg(projectRepo, "pull", "-u");

    // If we don't force the VFS to refresh, it won't find the files.
    refreshVfs();

    VirtualFile remoteCommonFile = remoteRepoVirtualFile.findChild("com").findChild("a.txt");
    assertNotNull(remoteCommonFile);

    VirtualFile projectCommonFile = projectRepoVirtualFile.findChild("com").findChild("a.txt");
    assertNotNull(projectCommonFile);

    // Make a change to the remote repo. After this, the remote repo will have history like:
    //  0:43a0c011b027
    //  1:762c0f6faa08
    editFileInCommand(myProject, remoteCommonFile, "made a change");
    runHg(remoteRepo, "commit", "-m", "updating history in remote repository");

    // Make a conflicting change to the project repo. After this, the project repo will have history like:
    //  0:43a0c011b027
    //  1:4292ea1cdcfb
    editFileInCommand(myProject, projectCommonFile, "conflicting content");
    runHg(projectRepo, "commit", "-m", "adding conflicting history to local repository");
    
    PreUpdateInformation preUpdateInformation = new PreUpdateInformation().getPreUpdateInformation();
    HgRevisionNumber incomingHead = preUpdateInformation.getIncomingHead();
    HgRevisionNumber headBeforeUpdate = preUpdateInformation.getHeadBeforeUpdate();

    // Update the project repo, which WILL cause a conflict. After this, the project repo will have
    //  a modified file (which, if this were real, would need to be resolved and then committed), and
    //  the history will be like:
    //  0:43a0c011b027
    //  1:4292ea1cdcfb
    //  2:762c0f6faa08
    List<VcsException> warnings = updateThroughPlugin();
    assertFalse(warnings.isEmpty());
    assertTrue(warnings.get(warnings.size()-1).getMessage().contains("conflicts"));
    assertTrue(warnings.get(warnings.size()-1).getMessage().contains("commit"));

    Pair<HgRevisionNumber, HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(projectRepoVirtualFile);
    assertNotNull(parents.first);
    assertNotNull(parents.second);

    // Compare the changeset, but NOT the revision, because the revision will be the same on both.
    //  See the running commentary above; prior to the update, both the project and remote repositories
    //  will have history that includes revision 0 and 1. However, revision 1 in the project repo
    //  is NOT the same as revision 1 in the remote repo. So in checking the parents, they ought to be:
    //    a) the changeset that was the head, prior to the update [4292ea1cdcfb]
    //    b) the changeset which was the head in the remote repo [762c0f6faa08]
    assertTrue(parents.first.getChangeset().equals(headBeforeUpdate.getChangeset()));
    assertTrue(parents.second.getChangeset().equals(incomingHead.getChangeset()));
  }

  @Test
  public void updateShouldNotMergeWithNonCommittedChanges() throws Exception {
    changeFile_A_AndCommitInRemoteRepository();

    //generate some extra local history
    createAndCommitNewFileInLocalRepository();

    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(myProject).parents(projectRepoVirtualFile).first;

    editFileInCommand(myProject, projectRepoVirtualFile.findFileByRelativePath("com/a.txt"), "modified file contents");

    assertUpdateThroughPluginFails();

    assertEquals( new HgHeadsCommand( myProject, projectRepoVirtualFile ).execute().size(), 2,
                  "Remote head should have been pulled in" );

    assertNull( new HgWorkingCopyRevisionsCommand( myProject ).parents( projectRepoVirtualFile ).second,
                  "No merge should have been attempted" );

    assertEquals( new HgWorkingCopyRevisionsCommand( myProject ).parents( projectRepoVirtualFile ).first, parentBeforeUpdate,
                  "No merge should have been attempted" );
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
    HgRegularUpdater updater = new HgRegularUpdater(myProject, projectRepoVirtualFile, new org.zmlx.hg4idea.provider.update.HgUpdater.UpdateConfiguration());
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    ArrayList<VcsException> nonFatalWarnings = new ArrayList<VcsException>();
    updater.update(updatedFiles, indicator, nonFatalWarnings);
    return nonFatalWarnings;
  }

  private void changeFile_A_AndCommitInRemoteRepository() throws IOException {
    fillFile(remoteRepo, new String[]{"com", "a.txt"}, "update file contents");
    runHg(remoteRepo, "add");
    runHg(remoteRepo, "commit", "-m", "Adding history to remote repository");

    assertEquals( determineNumberOfIncomingChanges( projectRepo ), 1,
                  "The remote repository should have gotten new history" );
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
      List<HgRevisionNumber> currentHeads = new HgHeadsCommand(myProject, projectRepoVirtualFile).execute();
      List<HgRevisionNumber> incomingChangesets = new HgIncomingCommand(myProject).execute(projectRepoVirtualFile);

      assertEquals(currentHeads.size(), 1);
      assertEquals(incomingChangesets.size(), 1);

      incomingHead = incomingChangesets.get(0);
      headBeforeUpdate = currentHeads.get(0);
      return this;
    }
  }
}

