// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.tasks.context.BranchContextTracker
import com.intellij.tasks.context.WorkingContextManager
import com.intellij.tasks.context.WorkingContextProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GitBranchContextTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp(): Unit = with(context) {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (!extensionArea.hasExtensionPoint(WorkingContextProvider.EP_NAME)) {
      extensionArea.registerExtensionPoint(WorkingContextProvider.EP_NAME.name,
                                           WorkingContextProvider::class.java.name,
                                           ExtensionPoint.Kind.INTERFACE,
                                           true)
      Disposer.register(disposable) {
        extensionArea.unregisterExtensionPoint(WorkingContextProvider.EP_NAME.name)
      }
    }
    WorkingContextManager.getInstance(project).enableUntil(disposable)
  }

  @Test
  fun testBranchListener(): Unit = with(context) {
    var fromBranch = ""
    var toBranch = ""

    class Listener : BranchChangeListener {
      override fun branchHasChanged(branchName: String) {
        toBranch = branchName
      }

      override fun branchWillChange(branchName: String) {
        fromBranch = branchName
      }
    }

    project.messageBus.connect(disposable).subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, Listener())

    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler(project))
    worker.checkoutNewBranch("foo", listOf(repo))
    UIUtil.pump()
    assertThat(toBranch).isEqualTo("foo")
    assertThat(fromBranch).isEqualTo("master")

    worker.checkout("master", false, listOf(repo))
    UIUtil.pump()
    assertThat(toBranch).isEqualTo("master")
    assertThat(fromBranch).isEqualTo("foo")
  }

  @Test
  fun testBranchContext(): Unit = with(context) {
    WorkingContextManager.getInstance(project).contextFile.delete()

    var value = ""

    class TestContextProvider : WorkingContextProvider() {
      override fun getId(): String = "test"
      override fun getDescription(): String = ""

      override fun saveContext(project: Project, toElement: Element) {
        toElement.text = value
      }

      override fun loadContext(project: Project, fromElement: Element) {
        value = fromElement.text
      }
    }

    ExtensionTestUtil.maskExtensions(WorkingContextProvider.EP_NAME, listOf(TestContextProvider()), disposable)

    // for local run make sure that used extensions are registered
    if (!NotificationGroupManager.getInstance().isGroupRegistered("Branch Context group")) {
      registerNotificationGroup()
      project.messageBus.connect(disposable).subscribe(BranchChangeListener.VCS_BRANCH_CHANGED,
                                                       BranchContextTracker(project))
    }

    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler(project))
    value = "master"
    worker.checkoutNewBranch("foo", listOf(repo))
    value = "foo"
    worker.checkout("master", false, listOf(repo))
    UIUtil.pump()
    assertThat(value).isEqualTo("master")
  }

  private fun registerNotificationGroup() {
    val notificationGroup = XmlSerializer.deserialize(JDOMUtil.load(
      """<notificationGroup id="Branch Context group" displayType="BALLOON"/>"""
    ), NotificationGroupEP::class.java)

    ExtensionPointName<NotificationGroupEP>("com.intellij.notificationGroup").point
      .registerExtension(notificationGroup, disposable)
  }
}