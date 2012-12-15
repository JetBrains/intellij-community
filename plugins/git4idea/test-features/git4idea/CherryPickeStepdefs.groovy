package git4idea
import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.notification.Notification
import com.intellij.openapi.vcs.FilePathImpl
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile
import com.intellij.testFramework.vcs.MockChangeListManager
import com.intellij.testFramework.vcs.MockContentRevision
import git4idea.history.browser.GitCherryPicker
import git4idea.history.browser.GitCommit
import git4idea.history.browser.SHAHash
import git4idea.history.wholeTree.AbstractHash
import git4idea.test.TestNotificator

import static com.intellij.dvcs.test.Executor.echo
import static cucumber.runtime.groovy.EN.*
import static git4idea.GitCucumberWorld.*
import static git4idea.test.GitExecutor.git
import static git4idea.test.GitScenarios.checkout
import static junit.framework.Assert.assertEquals
/**
 *
 * @author Kirill Likhodedov
 */

String remCommit

Given(~'^(enabled|disabled) auto-commit in the settings$') { String state ->
  boolean enabled = state.equals("enabled")
  myPlatformFacade.getSettings(myProject).setAutoCommitOnCherryPick(enabled);
}

Given(~'^branch "([^"]*)" with commit "([^"]*)" by "([^"]*)" modifying (.+):$') { String branch, String commitMessage, String author,
                                                                                  String file, String content ->
  checkout(myRepository, branch)
  echo(file, content)
  git("commit -am $commitMessage --author '$author <$author@example.com>'")
  checkout(myRepository, "master")
}

When(~'^I cherry-pick the commit "(.+)"$') { String msg ->
  String hash = git("log --grep $msg --pretty=%H --all");
  remCommit = hash
  new GitCherryPicker(myProject, myGit, myPlatformFacade, myPlatformFacade.getSettings(myProject).isAutoCommitOnCherryPick())
    .cherryPick(Collections.singletonMap(myRepository, Collections.singletonList(commit(hash, msg))));
}

private GitCommit commit(String hash, String message) {
  AbstractHash ahash = AbstractHash.create(hash);
  List<Change> changes = new ArrayList<Change>();
  changes.add(new Change(null, new MockContentRevision(new FilePathImpl(new MockVirtualFile("name")), VcsRevisionNumber.NULL)));
  return new GitCommit(NullVirtualFile.INSTANCE, ahash, SHAHash.emulate(ahash), "John Smith", null, null, message, message,
                       null, null, null, null, null, null, null, changes, 0);
}

Then(~'^the last commit is$') { String message ->
  String actual = git("log -1 --pretty=%B");
  message = message.replace("#hash", remCommit)
  assertEquals("Commit doesn't match", message, actual);
}

And(~'^notification "([^"]*)" is shown$') { String title ->
  assertEquals "Notification title is incorrect", title, lastNotification().title
}

private Notification lastNotification() {
  (myPlatformFacade.getNotificator(myProject) as TestNotificator).lastNotification
}

And(~'^no new changelists are created$') {->
  assertOnlyDefaultChangelist()
}

void assertOnlyDefaultChangelist() {
  String DEFAULT = MockChangeListManager.DEFAULT_CHANGE_LIST_NAME;
  assertChangeLists( [DEFAULT], DEFAULT)
}

void assertChangeLists(Collection<String> changeLists, String activeChangelist) {
  ChangeListManager changeListManager = myPlatformFacade.getChangeListManager(myProject)
  List<LocalChangeList> lists = changeListManager.changeLists
  Collection<String> listNames = lists.collect { it.name }
  assertEquals "Change lists are different", changeLists.toSet(), listNames.toSet()
  assertEquals "Wrong active changelist", activeChangelist, changeListManager.defaultChangeList.name
}
