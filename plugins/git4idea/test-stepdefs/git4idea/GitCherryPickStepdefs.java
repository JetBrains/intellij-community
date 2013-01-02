/*
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
package git4idea;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.dvcs.test.MockVcsHelper;
import com.intellij.dvcs.test.MockVirtualFile;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.testFramework.vcs.MockContentRevision;
import cucumber.annotation.en.And;
import cucumber.annotation.en.Given;
import cucumber.annotation.en.Then;
import cucumber.annotation.en.When;
import git4idea.history.browser.GitCherryPicker;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.AbstractHash;

import java.util.*;

import static com.intellij.dvcs.test.Executor.echo;
import static git4idea.GitCucumberWorld.*;
import static git4idea.test.GitExecutor.git;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author Kirill Likhodedov
 */

public class GitCherryPickStepdefs {

  @Given("^(enabled|disabled) auto-commit in the settings$")
  public void auto_commit_in_the_settings(String state) {
    boolean enabled = state.equals("enabled");
    myPlatformFacade.getSettings(myProject).setAutoCommitOnCherryPick(enabled);
  }

  @When("^I cherry-pick the commit (\\w+)$")
  public void I_cherry_pick_the_commit(String hash) {
    cherryPick(hash);
  }

  @When("^I cherry-pick commits (.+) and (.+)$")
  public void I_cherry_pick_commits(String severalCommits, String hash2) throws Throwable {

    String[] hashes = severalCommits.split(",");
    String[] allHashes = new String[hashes.length + 1];
    for (int i = 0; i < allHashes.length - 1; i++) {
      allHashes[i] = hashes[i].trim();
    }
    allHashes[allHashes.length - 1] = hash2;
    cherryPick(allHashes);
  }

  private static void cherryPick(String... virtualHashes) {
    List<GitCommit> commits = new ArrayList<GitCommit>();
    for (String virtualHash : virtualHashes) {
      commits.add(createMockCommit(virtualHash));
    }
    new GitCherryPicker(myProject, myGit, myPlatformFacade, mySettings.isAutoCommitOnCherryPick())
      .cherryPick(Collections.singletonMap(myRepository, commits));
  }

  private static GitCommit createMockCommit(String virtualHash) {
    CommitDetails realCommit = virtualCommits.getRealCommit(virtualHash);
    return mockCommit(realCommit.getHash(), realCommit.getMessage());
  }

  private static GitCommit mockCommit(String hash, String message) {
    AbstractHash ahash = AbstractHash.create(hash);
    List<Change> changes = new ArrayList<Change>();
    changes.add(new Change(null, new MockContentRevision(new FilePathImpl(new MockVirtualFile("name")), VcsRevisionNumber.NULL)));
    return new GitCommit(NullVirtualFile.INSTANCE, ahash, SHAHash.emulate(ahash), "John Smith", null, null, message, message,
                         null, null, null, null, null, null, null, changes, 0);
  }

  @When("^I cherry-pick the commit (\\w+) and( don't)? resolve conflicts$")
  public void I_cherry_pick_the_commit_and_resolve_conflicts(String hash, String negation) throws Throwable {
    if (negation == null) {
      resolveConflictsInFuture();
    }
    cherryPick(hash);
  }

  private static void resolveConflictsInFuture() {
    myVcsHelper.registerHandler(new MockVcsHelper.MergeHandler() {
      @Override
      public void showMergeDialog() {
        git("add -u .");
      }
    });
  }

  private static void commitInFuture() {
    myVcsHelper.registerHandler(new MockVcsHelper.CommitHandler() {
      @Override
      public boolean commit(String commitMessage) {
        git(String.format("commit -am '%s'", commitMessage));
        return true;
      }
    });
  }

  @When("^I cherry-pick the commit (.+), resolve conflicts and( don't)? commit$")
  public void I_cherry_pick_the_commit_resolve_conflicts_and_commit(String hash, String negation) throws Throwable {
    resolveConflictsInFuture();
    if (negation == null) {
      commitInFuture();
    }
    cherryPick(hash);
  }

  @Then("^the last commit is$")
  public void the_last_commit_is(String message) throws Throwable {
    git_log_should_return(1, message);
  }

  @Then("^the last commit is (.+)$")
  public void the_last_commit_is_hash(String hash) throws Throwable {
    assertEquals("The last commit hash doesn't match", virtualCommits.replaceVirtualHashes(hash), git("log -1 --pretty=%h"));
  }

  @Then("^`git log -(\\d+)` should return$")
  public void git_log_should_return(int commitNum, String messages) throws Throwable {
    List<String> expectedMessages = Arrays.asList(messages.split("-----"));

    final String RECORD_SEPARATOR = "@";
    String output = git("log -%s --pretty=%%B%s", String.valueOf(commitNum), RECORD_SEPARATOR);
    List<String> actualMessages = Arrays.asList(output.split("@"));

    for (int i = 0; i < expectedMessages.size(); i++) {
      String expectedMessage = expectedMessages.get(i).trim();
      String actualMessage = actualMessages.get(i).trim();
      expectedMessage = virtualCommits.replaceVirtualHashes(expectedMessage);
      assertEquals("Commit doesn't match", expectedMessage, trimHash(actualMessage));
    }
  }

  @And("^no new changelists are created$")
  public void no_new_changelists_are_created() {
    assertOnlyDefaultChangelist();
  }

  @Given("^(.+) is locally modified:$")
  public void is_locally_modified(String filename, String content) {
    echo(filename, content);
  }

  String trimHash(String commitMessage) {
    return commitMessage.replaceAll("([a-fA-F0-9]{7})[a-fA-F0-9]{33}", "$1");
  }

  @Then("^nothing is committed$")
  public void nothing_is_committed() {
    assertFalse("Working tree is unexpectedly clean", git("diff").trim().isEmpty());
  }

  @Then("^merge dialog should be shown$")
  public void merge_dialog_should_be_shown() throws Throwable {
    assertTrue("Merge dialog was not shown", myVcsHelper.mergeDialogWasShown());
  }

  @Then("^commit dialog should be shown$")
  public void commit_dialog_should_be_shown() throws Throwable {
    assertTrue("Commit dialog was not shown", myVcsHelper.commitDialogWasShown());
  }

  @Then("^active changelist is '(.+)'$")
  public void active_changelist_is(String name) throws Throwable {
    assertActiveChangeList(virtualCommits.replaceVirtualHashes(name));
  }

  private static void assertOnlyDefaultChangelist() {
    String DEFAULT = MockChangeListManager.DEFAULT_CHANGE_LIST_NAME;
    assertChangeLists(Collections.singleton(DEFAULT), DEFAULT);
  }

  private static void assertChangeLists(Collection<String> changeLists, String activeChangelist) {
    List<LocalChangeList> lists = myChangeListManager.getChangeLists();
    Collection<String> listNames = Collections2.transform(lists, new Function<LocalChangeList, String>() {
      @Override
      public String apply(LocalChangeList input) {
        return input.getName();
      }
    });
    assertEquals("Change lists are different", new ArrayList<String>(changeLists), new ArrayList<String>(listNames));
    assertActiveChangeList(activeChangelist);
  }

  private static void assertActiveChangeList(String name) {
    assertEquals("Wrong active changelist", name, myChangeListManager.getDefaultChangeList().getName());
  }

  @Given("^branch (.+)$")
  public void branch(String branchName) throws Throwable {
    git("branch " + branchName);
  }

}