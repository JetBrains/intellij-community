/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static git4idea.GitCucumberWorld.myProject;
import static git4idea.test.GitExecutor.cd;
import static git4idea.test.GitExecutor.git;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GitAddSteps {

  @Given("^unversioned file (.*)$")
  public void unversioned_file(String filePath) throws Throwable {
    cd(GitCucumberWorld.myRepository);
    VirtualFile file = VfsTestUtil.createFile(GitCucumberWorld.myProjectDir, filePath);
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
  }

  @When("^I add (.*) to VCS$")
  public void I_add_file_to_VCS(List<String> files) throws Throwable {
    List<VirtualFile> vFiles = ContainerUtil.map(files, new Function<String, VirtualFile>() {
      @Override
      public VirtualFile fun(@Nullable String file) {
        assertNotNull(file);
        if ("the project dir".equals(file)) {
          return GitCucumberWorld.myRepository.getRoot();
        }
        return GitCucumberWorld.myRepository.getRoot().findFileByRelativePath(file);
      }
    });
    GitCucumberWorld.myChangeListManager.ensureUpToDate(false);
    GitCucumberWorld.myChangeListManager.addUnversionedFiles(GitCucumberWorld.myChangeListManager.addChangeList("dummy", null), vFiles);
  }

  @Then("^(.*) should become ADDED$")
  public void file_should_become_ADDED(String filePath) throws Throwable {
    VirtualFile vf = GitCucumberWorld.myProjectDir.findFileByRelativePath(filePath);
    assertNotNull(vf);
    GitRepository repo = GitCucumberWorld.myPlatformFacade.getRepositoryManager(GitCucumberWorld.myProject).getRepositoryForFile(vf);
    String status = git(repo, "status --porcelain " + vf.getPath());
    assertTrue("File status is not-changed: " + status, !status.isEmpty());
    assertTrue("File status is not added: " + status, 'A' == status.charAt(0));
  }
}
