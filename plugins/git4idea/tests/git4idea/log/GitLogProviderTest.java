/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogBranchFilterImpl;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder;
import com.intellij.vcs.log.ui.filter.VcsLogTextFilterImpl;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.config.GitVersion;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.vcs.Executor.echo;
import static com.intellij.openapi.vcs.Executor.touch;
import static git4idea.test.GitExecutor.*;
import static git4idea.test.GitTestUtil.readAllRefs;
import static java.util.Collections.singleton;
import static org.junit.Assume.assumeTrue;

public class GitLogProviderTest extends GitSingleRepoTest {
  /**
   * Prior to 1.8.0 --regexp-ignore-case does not work when --fixed-strings parameter is specified, so can not filter case-insensitively without regex.
   */
  private static final GitVersion FIXED_STRINGS_WORKS_WITH_IGNORE_CASE = new GitVersion(1, 8, 0, 0);

  private GitLogProvider myLogProvider;
  private VcsLogObjectsFactory myObjectsFactory;

  public void setUp() throws Exception {
    super.setUp();
    myLogProvider = GitTestUtil.findGitLogProvider(myProject);
    myObjectsFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_init_with_tagged_branch() throws VcsException {
    prepareSomeHistory();
    List<VcsCommitMetadata> expectedLogWithoutTaggedBranch = log();
    createTaggedBranch();

    VcsLogProvider.DetailedLogData block =
      myLogProvider.readFirstBlock(projectRoot, new RequirementsImpl(1000, false, Collections.emptySet()));
    assertOrderedEquals(block.getCommits(), expectedLogWithoutTaggedBranch);
  }

  public void test_refresh_with_new_tagged_branch() throws VcsException {
    prepareSomeHistory();
    Set<VcsRef> prevRefs = readAllRefs(this, projectRoot, myObjectsFactory);
    createTaggedBranch();

    List<VcsCommitMetadata> expectedLog = log();
    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(projectRoot, new RequirementsImpl(1000, true, prevRefs));
    assertSameElements(block.getCommits(), expectedLog);
  }

  public void test_refresh_when_new_tag_moved() throws VcsException {
    prepareSomeHistory();
    Set<VcsRef> prevRefs = readAllRefs(this, projectRoot, myObjectsFactory);
    git("tag -f ATAG");

    List<VcsCommitMetadata> expectedLog = log();
    Set<VcsRef> refs = readAllRefs(this, projectRoot, myObjectsFactory);
    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(projectRoot, new RequirementsImpl(1000, true, prevRefs));
    assertSameElements(block.getCommits(), expectedLog);
    assertSameElements(block.getRefs(), refs);
  }

  public void test_new_tag_on_old_commit() throws VcsException {
    prepareSomeHistory();
    Set<VcsRef> prevRefs = readAllRefs(this, projectRoot, myObjectsFactory);
    List<VcsCommitMetadata> log = log();
    String firstCommit = log.get(log.size() - 1).getId().asString();
    git("tag NEW_TAG " + firstCommit);

    Set<VcsRef> refs = readAllRefs(this, projectRoot, myObjectsFactory);
    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(projectRoot, new RequirementsImpl(1000, true, prevRefs));
    assertSameElements(block.getRefs(), refs);
  }

  public void test_all_log_with_tagged_branch() throws VcsException {
    prepareSomeHistory();
    createTaggedBranch();
    List<VcsCommitMetadata> expectedLog = log();
    List<TimedVcsCommit> collector = ContainerUtil.newArrayList();
    //noinspection unchecked
    myLogProvider.readAllHashes(projectRoot, new CollectConsumer<>(collector));
    assertOrderedEquals(expectedLog, collector);
  }

  public void test_get_current_user() {
    VcsUser user = myLogProvider.getCurrentUser(projectRoot);
    assertNotNull("User is not defined", user);
    VcsUser expected = getDefaultUser();
    assertEquals("User name is incorrect", expected.getName(), user.getName());
    assertEquals("User email is incorrect", expected.getEmail(), user.getEmail());
  }

  public void test_dont_report_origin_HEAD() throws Exception {
    prepareSomeHistory();
    git("update-ref refs/remotes/origin/HEAD master");

    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(projectRoot,
                                                                        new RequirementsImpl(1000, false, Collections.emptySet()));
    assertFalse("origin/HEAD should be ignored", ContainerUtil.exists(block.getRefs(), ref -> ref.getName().equals("origin/HEAD")));
  }

  public void test_support_equally_named_branch_and_tag() throws Exception {
    prepareSomeHistory();
    git("branch build");
    git("tag build");

    VcsLogProvider.DetailedLogData data = myLogProvider.readFirstBlock(projectRoot,
                                                                       new RequirementsImpl(1000, true, Collections.emptySet()));
    List<VcsCommitMetadata> expectedLog = log();
    assertOrderedEquals(data.getCommits(), expectedLog);
    assertTrue(ContainerUtil.exists(data.getRefs(), ref -> ref.getName().equals("build") && ref.getType() == GitRefManager.LOCAL_BRANCH));
    assertTrue(ContainerUtil.exists(data.getRefs(), ref -> ref.getName().equals("build") && ref.getType() == GitRefManager.TAG));
  }

  public void test_filter_by_branch() throws Exception {
    List<String> hashes = generateHistoryForFilters(true, false);
    VcsLogBranchFilter branchFilter = VcsLogBranchFilterImpl.fromBranch("feature");
    repo.update();
    List<String> actualHashes = getFilteredHashes(new VcsLogFilterCollectionBuilder().with(branchFilter).build());
    assertEquals(hashes, actualHashes);
  }

  public void test_filter_by_branch_and_user() throws Exception {
    List<String> hashes = generateHistoryForFilters(false, false);
    VcsLogBranchFilter branchFilter = VcsLogBranchFilterImpl.fromBranch("feature");
    VcsUserImpl user = new VcsUserImpl(GitTestUtil.USER_NAME, GitTestUtil.USER_EMAIL);
    VcsLogUserFilter userFilter = new VcsLogUserFilterImpl(singleton(GitTestUtil.USER_NAME),
                                                           Collections.emptyMap(),
                                                           singleton(user));
    repo.update();
    List<String> actualHashes = getFilteredHashes(new VcsLogFilterCollectionBuilder().with(branchFilter).with(userFilter).build());
    assertEquals(hashes, actualHashes);
  }

  /*
   3 cases: no regexp + match case, regex + match case, regex + no matching case
    */
  public void test_filter_by_text() throws Exception {
    String initial = last(repo);

    String fileName = "f";

    touch(fileName, "content" + Math.random());
    String smallBrackets = addCommit(repo, "[git] " + fileName);
    echo(fileName, "content" + Math.random());
    String bigBrackets = addCommit(repo, "[GIT] " + fileName);
    echo(fileName, "content" + Math.random());
    String smallNoBrackets = addCommit(repo, "git " + fileName);
    echo(fileName, "content" + Math.random());
    String bigNoBrackets = addCommit(repo, "GIT " + fileName);

    String text = "[git]";
    assertEquals(Collections.singletonList(smallBrackets), getFilteredHashes(
      new VcsLogFilterCollectionBuilder().with(new VcsLogTextFilterImpl(text, false, true)).build()));
    assertEquals(Arrays.asList(bigNoBrackets, smallNoBrackets, bigBrackets, smallBrackets, initial),
                 getFilteredHashes(new VcsLogFilterCollectionBuilder().with(new VcsLogTextFilterImpl(text, true, false)).build()));
    assertEquals(Arrays.asList(smallNoBrackets, smallBrackets, initial),
                 getFilteredHashes(new VcsLogFilterCollectionBuilder().with(new VcsLogTextFilterImpl(text, true, true)).build()));
  }

  public void test_filter_by_text_no_regex() throws Exception {
    assumeFixedStringsWorks();

    String fileName = "f";

    touch(fileName, "content" + Math.random());
    String smallBrackets = addCommit(repo, "[git] " + fileName);
    echo(fileName, "content" + Math.random());
    String bigBrackets = addCommit(repo, "[GIT] " + fileName);
    echo(fileName, "content" + Math.random());

    assertEquals(Arrays.asList(bigBrackets, smallBrackets), getFilteredHashes(
      new VcsLogFilterCollectionBuilder().with(new VcsLogTextFilterImpl("[git]", false, false)).build()));
  }

  private void assumeFixedStringsWorks() {
    assumeTrue("Not testing: --regexp-ignore-case does not affect grep" +
               " or author filter when --fixed-strings parameter is specified prior to 1.8.0",
               vcs.getVersion().isLaterOrEqual(FIXED_STRINGS_WORKS_WITH_IGNORE_CASE));
  }

  private void filter_by_text_and_user(boolean regexp) throws Exception {
    List<String> hashes = generateHistoryForFilters(false, true);
    VcsUserImpl user = new VcsUserImpl(GitTestUtil.USER_NAME, GitTestUtil.USER_EMAIL);
    VcsLogUserFilter userFilter = new VcsLogUserFilterImpl(singleton(GitTestUtil.USER_NAME),
                                                           Collections.emptyMap(),
                                                           singleton(user));
    assertEquals(hashes, getFilteredHashes(new VcsLogFilterCollectionBuilder().with(userFilter).
      with(new VcsLogTextFilterImpl(regexp ? ".*" : "", regexp, false)).build()));
  }

  public void test_filter_by_text_with_regex_and_user() throws Exception {
    filter_by_text_and_user(true);
  }

  public void test_filter_by_simple_text_and_user() throws Exception {
    assumeFixedStringsWorks();
    filter_by_text_and_user(false);
  }

  public void test_short_details() throws Exception {
    prepareLongHistory(VcsFileUtil.FILE_PATH_LIMIT * 2 / 40);
    List<VcsCommitMetadata> log = log();

    final List<String> hashes = ContainerUtil.newArrayList();
    myLogProvider.readAllHashes(projectRoot, timedVcsCommit -> hashes.add(timedVcsCommit.getId().asString()));

    List<? extends VcsShortCommitDetails> shortDetails = myLogProvider.readShortDetails(projectRoot, hashes);

    Function<VcsShortCommitDetails, String> shortDetailsToString = getShortDetailsToString();
    assertOrderedEquals(ContainerUtil.map(shortDetails, shortDetailsToString), ContainerUtil.map(log, shortDetailsToString));
  }

  public void test_full_details() throws Exception {
    prepareLongHistory(VcsFileUtil.FILE_PATH_LIMIT * 2 / 40);
    List<VcsCommitMetadata> log = log();

    final List<String> hashes = ContainerUtil.newArrayList();
    myLogProvider.readAllHashes(projectRoot, timedVcsCommit -> hashes.add(timedVcsCommit.getId().asString()));

    List<VcsFullCommitDetails> result = ContainerUtil.newArrayList();
    myLogProvider.readFullDetails(projectRoot, hashes, result::add);

    // we do not check for changes here
    final Function<VcsShortCommitDetails, String> shortDetailsToString = getShortDetailsToString();
    Function<VcsCommitMetadata, String> metadataToString = details -> shortDetailsToString.fun(details) + "\n" + details.getFullMessage();
    assertOrderedEquals(ContainerUtil.map(result, metadataToString), ContainerUtil.map(log, metadataToString));
  }

  @NotNull
  private Function<VcsShortCommitDetails, String> getShortDetailsToString() {
    return details -> {
      String result = "";

      result += details.getId().toShortString() + "\n";
      result += details.getAuthorTime() + "\n";
      result += details.getAuthor() + "\n";
      result += details.getCommitTime() + "\n";
      result += details.getCommitter() + "\n";
      result += details.getSubject();

      return result;
    };
  }

  /**
   * Generates some history with two branches: master and feature, and made by two users.
   * Returns hashes of this history filtered by the given parameters:
   *
   * @param takeAllUsers if true, don't filter by users, otherwise filter by default user.
   */
  private List<String> generateHistoryForFilters(boolean takeAllUsers, boolean allBranches) {
    List<String> hashes = ContainerUtil.newArrayList();
    hashes.add(last(repo));

    GitTestUtil.setupUsername(myProject, "bob.smith", "bob.smith@example.com");
    if (takeAllUsers) {
      String commitByBob = tac(repo, "file.txt");
      hashes.add(commitByBob);
    }
    GitTestUtil.setupDefaultUsername(myProject);

    hashes.add(tac(repo, "file1.txt"));
    git("checkout -b feature");
    String commitOnlyInFeature = tac(repo, "file2.txt");
    hashes.add(commitOnlyInFeature);
    git("checkout master");
    String commitOnlyInMaster = tac(repo, "master.txt");
    if (allBranches) hashes.add(commitOnlyInMaster);

    Collections.reverse(hashes);
    refresh();
    return hashes;
  }

  @NotNull
  private List<String> getFilteredHashes(@NotNull VcsLogFilterCollection filters) throws VcsException {
    List<TimedVcsCommit> commits = myLogProvider.getCommitsMatchingFilter(projectRoot, filters, -1);
    return ContainerUtil.map(commits, commit -> commit.getId().asString());
  }

  private void prepareSomeHistory() {
    tac(repo, "a.txt");
    git("tag ATAG");
    tac(repo, "b.txt");
  }

  private void prepareLongHistory(int size) {
    for (int i = 0; i < size; i++) {
      String file = "a" + (i % 10) + ".txt";
      if (i < 10) {
        tac(repo, file);
      }
      else {
        modify(repo, file);
      }
    }
  }

  private void createTaggedBranch() {
    String hash = last(repo);
    tac(repo, "c.txt");
    tac(repo, "d.txt");
    tac(repo, "e.txt");
    git("tag poor-tag");
    git("reset --hard " + hash);
  }

  @NotNull
  private static VcsUser getDefaultUser() {
    return new VcsUserImpl(GitTestUtil.USER_NAME, GitTestUtil.USER_EMAIL);
  }

  @NotNull
  private List<VcsCommitMetadata> log() {
    String output = git("log --all --date-order --full-history --sparse --pretty='%H|%P|%ct|%s|%B'");
    final VcsUser defaultUser = getDefaultUser();
    final Function<String, Hash> TO_HASH = s -> HashImpl.build(s);
    return ContainerUtil.map(StringUtil.splitByLines(output), record -> {
      String[] items = ArrayUtil.toStringArray(StringUtil.split(record, "|", true, false));
      long time = Long.valueOf(items[2]) * 1000;
      return new VcsCommitMetadataImpl(TO_HASH.fun(items[0]), ContainerUtil.map(items[1].split(" "), TO_HASH), time,
                                       projectRoot, items[3], defaultUser, items[4], defaultUser, time);
    });
  }
}
