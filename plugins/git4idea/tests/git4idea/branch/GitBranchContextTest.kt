/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.branch

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.tasks.context.WorkingContextManager
import com.intellij.tasks.context.WorkingContextProvider
import com.intellij.testFramework.PlatformTestUtil
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase
import org.jdom.Element

class GitBranchContextTest: GitSingleRepoTest() {

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

    project.messageBus.connect().subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, Listener())

    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler())
    worker.checkoutNewBranch("foo", listOf(repo))
    TestCase.assertEquals("foo", toBranch)
    TestCase.assertEquals("master", fromBranch)

    worker.checkout("master", false, listOf(repo))
    TestCase.assertEquals("master", toBranch)
    TestCase.assertEquals("foo", fromBranch)
  }

  fun testBranchContext() {
    WorkingContextManager.getInstance(project).contextFile.delete()

    var value = ""

    class TestContextProvider: WorkingContextProvider() {

      override fun getId(): String = "test"
      override fun getDescription(): String = ""

      override fun saveContext(toElement: Element?) {
        toElement!!.text = value
      }

      override fun loadContext(fromElement: Element?) {
        value = fromElement!!.text;
      }
    }
    PlatformTestUtil.registerExtension(Extensions.getArea(project), WorkingContextProvider.EP_NAME, TestContextProvider(), testRootDisposable)

    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler())
    value = "master"
    worker.checkoutNewBranch("foo", listOf(repo))
    value = "foo"
    worker.checkout("master", false, listOf(repo))
    TestCase.assertEquals("master", value)
  }
}