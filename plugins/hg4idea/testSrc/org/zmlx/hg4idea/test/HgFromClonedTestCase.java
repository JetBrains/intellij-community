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

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.vcsUtil.VcsUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.zmlx.hg4idea.HgVcs;

import java.io.File;

import static org.testng.Assert.assertTrue;

public class HgFromClonedTestCase extends AbstractHgTestCase {

  protected File remoteRepo;
  protected File projectRepo;
  private TempDirTestFixture remoteRepoDir;
  private TempDirTestFixture projectRepoDir;
  protected VirtualFile projectRepoVirtualFile;

  @BeforeMethod
  public void setUp() throws Exception {
    setHGExecutablePath();

    remoteRepoDir = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    projectRepoDir = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();

    remoteRepo = createAndFillHgRepo(remoteRepoDir, "remote");
    projectRepo = cloneRemoteRepository(projectRepoDir, remoteRepo, "project");

    initProject(projectRepo);
    activateVCS(HgVcs.VCS_NAME);

    projectRepoVirtualFile = VcsUtil.getVirtualFile(projectRepo);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  private File cloneRemoteRepository(TempDirTestFixture projectRepoDir, File remoteRepo, String destination) throws Exception {
    projectRepoDir.setUp();
    String projectTempParent = projectRepoDir.getTempDirPath();

    File projectRepo = new File(projectTempParent, destination);
    runHg(null, "clone", remoteRepo.getAbsolutePath(), projectRepo.getAbsolutePath());
    assertTrue(projectRepo.exists());
    return projectRepo;
  }

  private File createAndFillHgRepo(TempDirTestFixture remoteRepoDir, String dirName) throws Exception {
    remoteRepoDir.setUp();
    File remoteRepo = new File(remoteRepoDir.getTempDirPath(), dirName);
    assertTrue(remoteRepo.mkdir());


    verify(runHg(remoteRepo, "init"));

    File aFile = fillFile(remoteRepo, new String[]{"com", "a.txt"}, "file contents");

    verify(runHg(remoteRepo, "add", aFile.getPath()));
    verify(runHg(remoteRepo, "status"), added("com", "a.txt"));
    verify(runHg(remoteRepo, "commit", "-m", "initial contents"));

    return remoteRepo;
  }

  @AfterMethod
  public void tearDown() throws Exception {
//    remoteRepoDir.tearDown();
//    projectRepoDir.tearDown();
  }
}
