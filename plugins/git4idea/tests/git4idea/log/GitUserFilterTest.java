/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.log;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogUserFilterTest;
import com.intellij.vcs.log.VcsUser;
import git4idea.config.GitVersion;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

import static git4idea.config.GitVersionSpecialty.LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR;
import static git4idea.test.GitExecutor.modify;

public class GitUserFilterTest extends GitSingleRepoTest {
  /**
   * git log supports multiple "--author=Author Name" arguments
   */
  private static final GitVersion LOG_AUTHOR_FILTER_SUPPORTS_MULTIPLE_AUTHORS = new GitVersion(1, 7, 4, 0);
  private VcsLogUserFilterTest myVcsLogUserFilterTest;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcsLogUserFilterTest = new VcsLogUserFilterTest(GitTestUtil.findGitLogProvider(myProject), myProject) {
      @NotNull
      protected String commit(@NotNull VcsUser user) {
        String userName = user.getName();
        String userEmail = user.getEmail();
        GitTestUtil.setupUsername(myProject, userName, userEmail);
        String commit = modify(GitUserFilterTest.this, "file.txt");
        GitTestUtil.setupDefaultUsername(myProject);
        return commit;
      }
    };
  }

  public void testFullMatching() throws Exception {
    myVcsLogUserFilterTest.testFullMatching();
  }

  public void testSynonyms() throws Exception {
    assumeMultipleUserFiltersWork();
    // commit by user with < or > in username does not contain them somewhy
    myVcsLogUserFilterTest.testSynonyms(ContainerUtil.newHashSet('<', '>'));
  }

  public void testTurkishLocale() throws Exception {
    assumeMultipleUserFiltersWork();
    myVcsLogUserFilterTest.testTurkishLocale();
  }

  private void assumeMultipleUserFiltersWork() {
    Assume.assumeTrue("Not testing: filtering by several users does not work on mac os with git prior to 1.7.4",
                      LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR.existsIn(vcs.getVersion()) ||
                      vcs.getVersion().isLaterOrEqual(LOG_AUTHOR_FILTER_SUPPORTS_MULTIPLE_AUTHORS));
  }

  public void testWeirdCharacters() throws Exception {
    myVcsLogUserFilterTest.testWeirdCharacters();
  }

  public void testWeirdNames() throws Exception {
    myVcsLogUserFilterTest.testWeirdNames();
  }

  public void testJeka() throws Exception {
    myVcsLogUserFilterTest.testJeka();
  }

  @Override
  protected void tearDown() throws Exception {
    myVcsLogUserFilterTest = null;

    super.tearDown();
  }
}
