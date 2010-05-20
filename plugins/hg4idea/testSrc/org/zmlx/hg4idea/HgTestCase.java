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
package org.zmlx.hg4idea;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.vcsUtil.VcsUtil;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;

public abstract class HgTestCase extends AbstractVcsTestCase {

  private File repo;

  @BeforeMethod
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    TempDirTestFixture testFixture = fixtureFactory.createTempDirTestFixture();
    testFixture.setUp();

    repo = new File(testFixture.getTempDirPath(), "repo");
    Assert.assertTrue(repo.mkdir());

    myClientBinaryPath = new File("/usr/bin");

    verify(runClient("hg", null, repo, new String[] {"init"}));
    initProject(repo);
    activateVCS(HgVcs.VCS_NAME);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  protected ProcessOutput runHg(String... commandLine) throws IOException {
    return runClient("hg", null, repo, commandLine);
  }

  protected void enableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(
      HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
    );
  }

  protected void disableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(
      HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
    );
  }

  protected VirtualFile makeFile(File file) throws IOException {
    Assert.assertTrue(file.createNewFile());
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(myWorkingCopyDir);
    LocalFileSystem.getInstance().refresh(false);
    return VcsUtil.getVirtualFile(file);
  }

}
