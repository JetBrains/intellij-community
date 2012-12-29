package git4idea;/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.dvcs.test.MockProject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import cucumber.annotation.After;
import cucumber.annotation.Before;
import cucumber.annotation.en.Given;
import git4idea.test.GitTestImpl;
import git4idea.test.GitTestPlatformFacade;

import java.io.File;
import java.io.IOException;

import static com.intellij.dvcs.test.Executor.cd;
import static com.intellij.dvcs.test.Executor.mkdir;
import static git4idea.GitCucumberWorld.*;
import static git4idea.test.GitExecutor.git;
import static git4idea.test.GitExecutor.touch;
import static git4idea.test.GitScenarios.checkout;
import static git4idea.test.GitTestInitUtil.createRepository;

/**
 * @author Kirill Likhodedov
 */
public class GeneralStepdefs {

  @Before
  public void setUpProject() throws IOException {
    myTestRoot = FileUtil.createTempDirectory("", "").getPath();
    cd(myTestRoot);
    myProjectRoot = mkdir("project");
    myProject = new MockProject(myProjectRoot);
    myPlatformFacade = new GitTestPlatformFacade();
    myGit = new GitTestImpl();
    mySettings = myPlatformFacade.getSettings(myProject);

    cd(myProjectRoot);
    myRepository = createRepository(myProjectRoot, myPlatformFacade, myProject);

    virtualCommits = new GitTestVirtualCommitsHolder();
  }

  @After
  public void cleanup() {
    FileUtil.delete(new File(myTestRoot));
    Disposer.dispose(myProject);
  }

  @Given("^file (.*) \"(.*)\" on master$")
  public void file_file_txt_on_master(String filename, String content) throws Throwable {
    checkout(myRepository, "master");
    touch(filename, content);
    git("add %s", filename);
    git("commit -m 'adding %s'", filename);
  }

  @Given("^commit (.+) on branch (.+)$")
  public void commit_on_branch_feature(String hash, String branch, String commitDetails) throws Throwable {
    CommitDetails commit = CommitDetails.parse(hash, commitDetails);
    checkout(branch);
    commit.apply();
    checkout("master");
  }

}
