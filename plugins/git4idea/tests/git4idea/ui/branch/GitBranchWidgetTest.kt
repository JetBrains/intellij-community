// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.messages.MessageBusConnection
import git4idea.branch.GitBranchIncomingOutgoingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class GitBranchWidgetTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project get() = projectFixture.get()

  @Test
  fun `test Git event during the widget construction`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      Disposer.dispose(EventDuringConstructionWidget(project))
    }
  }

  @Test
  fun `test Git event during the subclass construction`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      Disposer.dispose(EventDuringSubclassConstructionWidget(project))
    }
  }

  @Test
  fun `test events after the widget install`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val widget = UpdateCountingWidget(project)
      try {
        widget.install(WindowManager.getInstance().getStatusBar(project)!!)
        val updatesAfterInstall = widget.updates.get()

        project.messageBus.syncPublisher(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED).mappingChanged()
        project.messageBus.syncPublisher(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED).incomingOutgoingInfoChanged()

        assertEquals(updatesAfterInstall + 2, widget.updates.get())
      }
      finally {
        Disposer.dispose(widget)
      }
    }
  }
}

/**
 * Publishes a Git event from the constructor of the base class, before the constructor of [GitBranchWidget] ends.
 */
private class EventDuringConstructionWidget(project: Project) : GitBranchWidget(project) {
  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
  override fun registerCustomListeners(connection: MessageBusConnection) {
    super.registerCustomListeners(connection)
    project.messageBus.syncPublisher(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED).incomingOutgoingInfoChanged()
  }
}

/**
 * Publishes a Git event after the constructor of [GitBranchWidget] ends, and before this class sets [updates].
 */
private class EventDuringSubclassConstructionWidget(project: Project) : GitBranchWidget(project) {
  init {
    project.messageBus.syncPublisher(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED).incomingOutgoingInfoChanged()
  }

  private val updates = AtomicInteger()

  override fun updateLater() {
    updates.incrementAndGet()
  }
}

private class UpdateCountingWidget(project: Project) : GitBranchWidget(project) {
  val updates = AtomicInteger()

  override fun updateLater() {
    updates.incrementAndGet()
  }
}
