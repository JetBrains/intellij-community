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
import com.intellij.dvcs.test.MockVcsHelper;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import cucumber.annotation.After;
import cucumber.annotation.Before;
import cucumber.annotation.en.And;
import cucumber.annotation.en.Given;
import cucumber.annotation.en.Then;
import git4idea.test.GitTestImpl;
import git4idea.test.GitTestPlatformFacade;
import git4idea.test.TestNotificator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.intellij.dvcs.test.Executor.cd;
import static com.intellij.dvcs.test.Executor.mkdir;
import static git4idea.GitCucumberWorld.*;
import static git4idea.test.GitExecutor.git;
import static git4idea.test.GitExecutor.touch;
import static git4idea.test.GitScenarios.checkout;
import static git4idea.test.GitTestInitUtil.createRepository;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    myVcsHelper = (MockVcsHelper) myPlatformFacade.getVcsHelper(myProject);
    myChangeListManager = myPlatformFacade.getChangeListManager(myProject);

    cd(myProjectRoot);
    myRepository = createRepository(myProjectRoot, myPlatformFacade, myProject);

    virtualCommits = new GitTestVirtualCommitsHolder();
  }

  @After
  public void cleanup() {
    FileUtil.delete(new File(myTestRoot));
    Disposer.dispose(myProject);
  }

  @Given("^file (.+) '(.+)'$")
  public void file_untracked_txt(String fileName, String content) throws Throwable {
    touch(fileName, content);
  }

  @Given("^new committed file (.*) '(.*)'$")
  public void new_committed_file(String filename, String content) throws Throwable {
    touch(filename, content);
    git("add %s", filename);
    git("commit -m 'adding %s'", filename);
  }

  @Given("^commit (.+) on branch (.+)$")
  public void commit_on_branch(String hash, String branch, String commitDetails) throws Throwable {
    CommitDetails commit = CommitDetails.parse(hash, commitDetails);
    // we implicitly assume that we always are on master (maybe except some certain test cases which should handle it separately
    if (!branch.equals("master")) {
      checkout(branch);
    }
    commit.apply();
    if (!branch.equals("master")) {
      checkout("master");
    }
  }

  @Then("^(success|warning|error) notification is shown '(.+)'$")
  public void error_notification_is_shown(String notificationType, String title, String content) {
    NotificationType type = notificationType.equals("success") ? NotificationType.INFORMATION :
                            notificationType.equals("warning") ? NotificationType.WARNING :
                            notificationType.equals("error") ? NotificationType.ERROR : null;
    assertEquals("Notification type is incorrect", type, lastNotification().getType());
    assertEquals("Notification title is incorrect", title, lastNotification().getTitle());
    assertNotificationContent(content, lastNotification().getContent());
  }

  private static void assertNotificationContent(String expected, String actual) {
    expected = virtualCommits.replaceVirtualHashes(expected);
    assertEquals("Notification content is incorrect", expected, adjustNotificationContent(actual));

  }

  private static String adjustNotificationContent(String content) {
    return content.replaceAll("<br/>", "\n")      // we don't want to type <br/> in features
                  .replaceAll("([^\n])?<hr/>", "$1\n<hr/>")         // surround <hr/> with newlines
                  .replaceAll("<hr/>([^\n])?", "<hr/>\n$1");

  }

  private static Notification lastNotification() {
    return ((TestNotificator)myPlatformFacade.getNotificator(myProject)).getLastNotification();
  }

  @And("^no notification is shown$")
  public void no_notification_is_shown() throws Throwable {
    assertNull("Notification should not be shown", lastNotification());
  }

  @Given("^new committed files (.+) with initial content$")
  public void new_committed_files_file_txt_a_txt_b_txt_with_initial_content(String listOfFiles) throws Throwable {
    List<String> files = splitByComma(listOfFiles);
    for (String file : files) {
      touch(file, "initial content");
    }
    git("add %s", StringUtil.join(files, " "));
    git("commit -m 'adding files with initial content'");
  }

  public static List<String> splitByComma(String listOfItems) {
    return Arrays.asList(listOfItems.split(", ?"));
  }

  @Given("^branch (.+)$")
  public void branch(String branchName) throws Throwable {
    git("branch " + branchName);
  }

}
