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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import git4idea.cherrypick.GitCherryPicker;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitHistoryUtils;
import git4idea.test.MockVcsHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.echo;
import static git4idea.GitCucumberWorld.*;
import static git4idea.test.GitExecutor.git;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class GitCherryPickStepdefs {

  @Given("^(enabled|disabled) auto-commit in the settings$")
  public void auto_commit_in_the_settings(String state) {
    boolean enabled = state.equals("enabled");
    mySettings.setAutoCommitOnCherryPick(enabled);
  }

  @When("^I cherry-pick the commit (\\w+)$")
  public void I_cherry_pick_the_commit(String hash) throws VcsException {
    cherryPick(hash);
  }

  @When("^I cherry-pick commits (.+) and (\\w+)$")
  public void I_cherry_pick_commits(String severalCommits, String hash2) throws Throwable {

    String[] hashes = severalCommits.split(",");
    String[] allHashes = new String[hashes.length + 1];
    for (int i = 0; i < allHashes.length - 1; i++) {
      allHashes[i] = hashes[i].trim();
    }
    allHashes[allHashes.length - 1] = hash2;
    cherryPick(allHashes);
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

  private static void commitInFuture(final int times) {
    myVcsHelper.registerHandler(new MockVcsHelper.CommitHandler() {

      private int myCommitRequests;

      @Override
      public boolean commit(String commitMessage) {
        if (myCommitRequests >= times) {
          return false;
        }
        myCommitRequests++;
        git(String.format("commit -am '%s'", commitMessage));
        return true;
      }
    });
  }

  private static void commitInFuture() {
    commitInFuture(Integer.MAX_VALUE);
  }

  @When("^I cherry-pick the commit (.+), resolve conflicts and( don't)? commit$")
  public void I_cherry_pick_the_commit_resolve_conflicts_and_commit(String hash, String negation) throws Throwable {
    resolveConflictsInFuture();
    if (negation == null) {
      commitInFuture();
    }
    cherryPick(hash);
  }

  @When("^I cherry-pick the commit (\\w+) and( don't)? commit$")
  public void I_cherry_pick_the_commit_hash_and_commit(String hash, String negation) throws Throwable {
    if (negation == null) {
      commitInFuture();
    }
    cherryPick(hash);
  }

  @When("^I cherry-pick commits (.+) and commit both of them$")
  public void I_cherry_pick_commits_and_commit_both_of_them(String listOfHashes) throws Throwable {
    commitInFuture();
    cherryPick(GeneralStepdefs.splitByComma(listOfHashes));
  }

  @When("^I cherry-pick commits (.+), but commit only the first one$")
  public void I_cherry_pick_commits_but_commit_only_the_first_one(String listOfHashes) throws Throwable {
    commitInFuture(1);
    cherryPick(GeneralStepdefs.splitByComma(listOfHashes));
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
    boolean fullBody = GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(myVcs.getVersion());
    String data = fullBody ? "%B" : "%s%b";
    String output = git("log -%s --pretty=%s%s", String.valueOf(commitNum), data, RECORD_SEPARATOR);
    List<String> actualMessages = Arrays.asList(output.split(RECORD_SEPARATOR));

    for (int i = 0; i < expectedMessages.size(); i++) {
      String expectedMessage = StringUtil.convertLineSeparators(expectedMessages.get(i).trim());
      String actualMessage = StringUtil.convertLineSeparators(actualMessages.get(i).trim());
      if (!fullBody) {
        // the subject (%s) contains both "fix #1" and "cherry-picked from <hash>" in a single line
        // so let's compare without taking line breaks and spaces into consideration
        expectedMessage = expectedMessage.replace("\n", "").replace(" ", "");
        actualMessage = actualMessage.replace("\n", "").replace(" ", "");
      }
      else {
        // replace just double \n between subject and body to avoid lengthy feature steps
        expectedMessage = expectedMessage.replace("\n\n", "\n");
        actualMessage = actualMessage.replace("\n\n", "\n");
      }
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
  public void nothing_is_committed() throws Throwable {
    working_tree_is_dirty();
  }

  @And("^working tree is dirty$")
  public void working_tree_is_dirty() throws Throwable {
    assertFalse("Working tree is unexpectedly clean", git("diff").trim().isEmpty() && git("diff --cached").trim().isEmpty());
  }

  @Then("^merge dialog should be shown$")
  public void merge_dialog_should_be_shown() throws Throwable {
    assertTrue("Merge dialog was not shown", myVcsHelper.mergeDialogWasShown());
  }

  @Then("^commit dialog should be shown$")
  public void commit_dialog_should_be_shown() throws Throwable {
    assertTrue("Commit dialog was not shown", myVcsHelper.commitDialogWasShown());
  }

  @Then("^there is changelist '(.*)'$")
  public void there_is_changelist(@NotNull final String name) throws Throwable {
    List<LocalChangeList> changeLists = myChangeListManager.getChangeListsCopy();
    assertTrue("Didn't find changelist with name '" + name + "' among :" + changeLists,
               ContainerUtil.exists(changeLists, new Condition<LocalChangeList>() {
                 @Override
                 public boolean value(LocalChangeList list) {
                   return list.getName().equals(virtualCommits.replaceVirtualHashes(name));
                 }
               }));
  }

  private static void assertOnlyDefaultChangelist() {
    String DEFAULT = MockChangeListManager.DEFAULT_CHANGE_LIST_NAME;
    assertEquals("Only default changelist is expected", 1, myChangeListManager.getChangeListsNumber());
    assertEquals("Default changelist is not active", DEFAULT, myChangeListManager.getDefaultChangeList().getName());
  }

  private static void cherryPick(final List<String> virtualHashes) throws VcsException {
    List<VcsFullCommitDetails> commits = loadDetails(ContainerUtil.map(virtualHashes, new Function<String, String>() {
      @Override
      public String fun(String virtualHash) {
        return virtualCommits.getRealCommit(virtualHash).getHash();
      }
    }), myProjectDir);
    new GitCherryPicker(myProject, myGit).cherryPick(commits);
  }

  private static List<VcsFullCommitDetails> loadDetails(List<String> hashes, @NotNull VirtualFile root) throws VcsException {
    String noWalk = GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(myVcs.getVersion()) ? "--no-walk=unsorted" : "--no-walk";
    List<String> params = new ArrayList<>();
    params.add(noWalk);
    params.addAll(hashes);
    return new ArrayList<>(GitHistoryUtils.history(myProject, root, ArrayUtil.toStringArray(params)));
  }

  private static void cherryPick(String... virtualHashes) throws VcsException {
    cherryPick(Arrays.asList(virtualHashes));
  }
}