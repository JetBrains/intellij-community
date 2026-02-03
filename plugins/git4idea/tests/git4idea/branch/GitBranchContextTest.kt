// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.tasks.context.WorkingContextManager
import com.intellij.tasks.context.WorkingContextProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.ui.UIUtil
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase
import org.jdom.Element

class GitBranchContextTest: GitSingleRepoTest() {
  override fun setUp() {
    super.setUp()
    WorkingContextManager.getInstance(project).enableUntil(testRootDisposable)
  }

  fun testBranchListener() {
    var fromBranch = ""
    var toBranch = ""
    class Listener: BranchChangeListener {
      override fun branchHasChanged(branchName: String) {
        toBranch = branchName
      }

      override fun branchWillChange(branchName: String) {
        fromBranch = branchName
      }
    }

    project.messageBus.connect(testRootDisposable).subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, Listener())

    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler(project))
    worker.checkoutNewBranch("foo", listOf(repo))
    UIUtil.pump()
    TestCase.assertEquals("foo", toBranch)
    TestCase.assertEquals("master", fromBranch)

    worker.checkout("master", false, listOf(repo))
    UIUtil.pump()
    TestCase.assertEquals("master", toBranch)
    TestCase.assertEquals("foo", fromBranch)
  }

  fun testBranchContext() {
    WorkingContextManager.getInstance(project).contextFile.delete()

    var value = ""

    class TestContextProvider: WorkingContextProvider() {
      override fun getId(): String = "test"
      override fun getDescription(): String = ""

      override fun saveContext(project: Project, toElement: Element) {
        toElement.text = value
      }

      override fun loadContext(project: Project, fromElement: Element) {
        value = fromElement.text
      }
    }
    ExtensionTestUtil.maskExtensions(WorkingContextProvider.EP_NAME, listOf(TestContextProvider()), testRootDisposable)

    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler(project))
    value = "master"
    worker.checkoutNewBranch("foo", listOf(repo))
    value = "foo"
    worker.checkout("master", false, listOf(repo))
    UIUtil.pump()
    TestCase.assertEquals("master", value)
  }
}