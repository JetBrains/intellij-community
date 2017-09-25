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
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import static git4idea.test.GitExecutor.modify;

public class GitUserFilterTest extends GitSingleRepoTest {
  private VcsLogUserFilterTest myVcsLogUserFilterTest;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcsLogUserFilterTest = new VcsLogUserFilterTest(GitTestUtil.findGitLogProvider(myProject), myProject) {
      @NotNull
      protected String commit(@NotNull VcsUser user) {
        GitTestUtil.setupUsername(user.getName(), user.getEmail());
        String commit = modify("file.txt");
        GitTestUtil.setupDefaultUsername();
        return commit;
      }
    };
  }

  public void testFullMatching() throws Exception {
    myVcsLogUserFilterTest.testFullMatching();
  }

  public void testSynonyms() throws Exception {
    // commit by user with < or > in username does not contain them somewhy
    myVcsLogUserFilterTest.testSynonyms(ContainerUtil.newHashSet('<', '>'));
  }

  public void testTurkishLocale() throws Exception {
    myVcsLogUserFilterTest.testTurkishLocale();
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
