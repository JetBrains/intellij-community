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
package git4idea.cherrypick

import git4idea.test.*

class GitCherryPickAutoCommitTest : GitCherryPickTest() {

  override fun setUp() {
    super.setUp()
    settings.isAutoCommitOnCherryPick = true
  }

  fun `test simple cherry-pick`() {
    branch("feature")
    val commit = file("c.txt").create().addCommit("fix #1").hash()
    checkout("feature")

    cherryPick(commit)

    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} fix #1")
    assertLastMessage("fix #1\n\n(cherry picked from commit ${commit})")
    changeListManager.assertOnlyDefaultChangelist()
  }

  fun `test dirty tree conflicting with commit`() {
    `check dirty tree conflicting with commit`()
  }

  fun `test untracked file conflicting with commit`() {
    `check untracked file conflicting with commit`()
  }

  fun `test conflict with cherry-picked commit should show merge dialog`() {
    `check conflict with cherry-picked commit should show merge dialog`()
  }

  fun `test unresolved conflict with cherry-picked commit should produce a changelist`() {
    val commit = repo.prepareConflict()
    `do nothing on merge`()

    cherryPick(commit)

    `assert merge dialog was shown`()
    changeListManager.assertChangeListExists("on_master\n\n(cherry picked from commit ${shortHash(commit)})")
    assertWarningNotification("Cherry-picked with conflicts", """
      ${shortHash(commit)} on_master
      Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>
      """)
  }

  fun `test resolve conflicts and commit`() {
    `check resolve conflicts and commit`()
  }

  fun `test resolve conflicts but cancel commit`() {
    val commit = repo.prepareConflict()
    `mark as resolved on merge`()
    vcsHelper.onCommit { false }

    cherryPick(commit)

    `assert merge dialog was shown`()
    `assert commit dialog was shown`()
    changeListManager.assertChangeListExists("on_master\n\n(cherry picked from commit ${shortHash(commit)})")
    assertNoNotification()
  }

  fun `test cherry-pick 2 commits`() {
    branch("feature")
    val commit1 = file("one.txt").create().addCommit("fix #1").hash()
    val commit2 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")

    cherryPick(commit1, commit2)

    assertLogMessages("""
      fix #2

      (cherry picked from commit $commit2)""", """
      fix #1

      (cherry picked from commit $commit1)""")
    assertSuccessfulNotification("Cherry-pick successful","""
      ${shortHash(commit1)} fix #1
      ${shortHash(commit2)} fix #2
""")
  }

  fun `test cherry-picked 3 commits where 2nd conflicts with local changes`() {
    val common = file("common.txt")
    common.create("initial content\n").addCommit("common")
    branch("feature")
    val commit1 = file("one.txt").create().addCommit("fix #1").hash()
    val commit2 = common.append("on master\n").addCommit("appended common").hash()
    val commit3 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")
    common.append("on feature\n")

    cherryPick(commit1, commit2, commit3)

    assertErrorNotification("Cherry-pick Failed", """
      ${shortHash(commit2)} appended common
      Your local changes would be overwritten by cherry-pick.
      Commit your changes or stash them to proceed.
      However cherry-pick succeeded for the following commit:
      ${shortHash(commit1)} fix #1""")

  }

  fun `test cherry-pick 3 commits, where second conflicts with master`() {
    val common = file("common.txt")
    common.create("initial content\n").addCommit("common")
    branch("feature")
    val commit1 = file("one.txt").create().addCommit("fix #1").hash()
    val commit2 = common.append("on master\n").addCommit("appended common").hash()
    val commit3 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")
    common.append("on feature\n").addCommit("appended on feature").hash()
    `do nothing on merge`()

    cherryPick(commit1, commit2, commit3)

    `assert merge dialog was shown`()
    assertLastMessage("fix #1\n\n(cherry picked from commit $commit1)")
  }

  // IDEA-73548
  fun `test nothing to commit`() {
    val commit = file("c.txt").create().addCommit("fix #1").hash()
    repo.checkoutNew("feature")

    cherryPick(commit)

    assertWarningNotification("Nothing to cherry-pick",
                              "All changes from ${shortHash(commit)} have already been applied")
  }

  // IDEA-73548
  fun `test several commits one of which have already been applied`() {
    file("common.txt").create("common content\n").addCommit("common file")
    repo.checkoutNew("feature")
    val commit1 = file("a.txt").create("initial\n").addCommit("fix #1").hash()
    val emptyCommit = file("common.txt").append("more to common\n").addCommit("to common").hash()
    val commit3 = file("a.txt").append("more\n").addCommit("fix #2").hash()
    checkout("master")
    file("common.txt").append("more to common\n").addCommit("to common from master")

    cherryPick(commit1, emptyCommit, commit3)

    assertLogMessages(
      """
      fix #2

      (cherry picked from commit $commit3)""",
      """
      fix #1

      (cherry picked from commit $commit1)""")
    assertSuccessfulNotification("Cherry-picked 2 commits from 3","""
      ${shortHash(commit1)} fix #1
      ${shortHash(commit3)} fix #2
      ${shortHash(emptyCommit)} was skipped, because all changes have already been applied.""")
  }
}