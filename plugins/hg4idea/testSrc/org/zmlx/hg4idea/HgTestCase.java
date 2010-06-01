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

import com.intellij.execution.process.*;
import com.intellij.openapi.vcs.*;
import com.intellij.testFramework.fixtures.*;
import org.testng.*;
import org.testng.annotations.*;

import java.io.*;

public abstract class HgTestCase extends AbstractHgTestCase {

  protected File projectRepo;
  private TempDirTestFixture tempDirTestFixture;

  @BeforeMethod
  public void setUp() throws Exception {
    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    tempDirTestFixture.setUp();
    projectRepo = new File(tempDirTestFixture.getTempDirPath(), "repo");
    Assert.assertTrue(projectRepo.mkdir());

    ProcessOutput processOutput = runHg(projectRepo, "init");
    verify(processOutput);
    initProject(projectRepo);
    activateVCS(HgVcs.VCS_NAME);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @AfterMethod
  public void tearDown() throws Exception {
//    tempDirTestFixture.tearDown();
  }

  protected ProcessOutput runHgOnProjectRepo(String... commandLine) throws IOException {
    return runHg(projectRepo, commandLine);
  }

  protected HgFile getHgFile(String... filepath) {
    File fileToInclude = projectRepo;
    for (String path : filepath) {
      fileToInclude = new File(fileToInclude, path);
    }
    return new HgFile(myWorkingCopyDir, fileToInclude);
  }
}
