/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.testng.annotations.BeforeMethod;
import org.zmlx.hg4idea.HgVcs;

import java.io.File;

/**
 * The parent of all tests, where at least two repositories communicate with each other.
 * This is used to test collaborative tasks, such as push, pull, merge and others.
 * @author Kirill Likhodedov
 */
public class HgCollaborativeTestCase extends HgAbstractTestCase {

  protected HgTestRepository myParentRepo;
  protected HgTestRepository myRepo;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myParentRepo = createRepository();
    myRepo = cloneFrom(myParentRepo);

    myProjectDir = new File(myRepo.getDirFixture().getTempDirPath());

    initProject(myProjectDir);
    activateVCS(HgVcs.VCS_NAME);
    myChangeListManager = new HgTestChangeListManager(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  /**
   * Clones a repository from the given one. New repository is located in a temporary test directory.
   * @param parent repository to clone from.
   * @return New repository cloned from the given parent.
   */
  protected HgTestRepository cloneFrom(HgTestRepository parent) throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final ProcessOutput processOutput = runHg(null, "clone", parent.getDirFixture().getTempDirPath(), dirFixture.getTempDirPath());
    verify(processOutput);
    return new HgTestRepository(this, dirFixture);
  }

}
