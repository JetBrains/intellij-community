// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import static git4idea.test.GitTestUtil.makeCommit;

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
      @Override
      @NotNull
      protected String commit(@NotNull VcsUser user) {
        return makeCommit(GitUserFilterTest.this, user, "file.txt");
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

  public void testNameAtSurnameEmails() throws Exception {
    myVcsLogUserFilterTest.testNameAtSurnameEmails();
  }

  @Override
  protected void tearDown() {
    myVcsLogUserFilterTest = null;

    super.tearDown();
  }
}
