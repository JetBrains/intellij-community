// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class StartupManagerActivityHoldTest {
  private val project by projectFixture()
  private val disposable by disposableFixture()

  @Test
  fun `an added project activity runs at once without a hold`(): Unit = timeoutRunBlocking {
    StartupManagerImpl.addActivityEpListener(project)
    val activity = RecordingActivity()

    StartupActivity.POST_STARTUP_ACTIVITY.point.registerExtension(activity, disposable)

    activity.ran.await()
  }

  @Test
  fun `an added project activity waits for the end of the hold`(): Unit = timeoutRunBlocking {
    StartupManagerImpl.addActivityEpListener(project)
    val activity = RecordingActivity()
    val hold = startupManager().holdActivities()
    try {
      StartupActivity.POST_STARTUP_ACTIVITY.point.registerExtension(activity, disposable)

      assertNull(withTimeoutOrNull(300.milliseconds) { activity.ran.await() })
      assertFalse(activity.ran.isCompleted)
    }
    finally {
      hold.finish()
    }

    activity.ran.await()
  }

  @Test
  fun `an added legacy dumb-aware activity waits for the end of the hold`(): Unit = timeoutRunBlocking {
    assertLegacyActivityWaitsForHold(RecordingLegacyDumbAwareActivity())
  }

  @Test
  fun `an added legacy activity that needs the smart mode waits for the end of the hold`(): Unit = timeoutRunBlocking {
    assertLegacyActivityWaitsForHold(RecordingLegacyActivity())
  }

  /**
   * The extension point takes only a [ProjectActivity] from code, so a legacy activity starts the way the listener starts it.
   * The project of the fixture is dumb until a test asks for its indexes, and a legacy activity can wait for the smart mode.
   */
  private suspend fun assertLegacyActivityWaitsForHold(activity: RecordingLegacyActivity) {
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val startupManager = startupManager()
    val hold = startupManager.holdActivities()
    try {
      startupManager.startAddedActivity(activity)

      assertNull(withTimeoutOrNull(300.milliseconds) { activity.ran.await() })
      assertFalse(activity.ran.isCompleted)
    }
    finally {
      hold.finish()
    }

    activity.ran.await()
  }

  @Test
  fun `a second hold fails while the first one is active`(): Unit = timeoutRunBlocking {
    val startupManager = startupManager()
    val hold = startupManager.holdActivities()
    try {
      assertThrows<IllegalStateException> { startupManager.holdActivities() }
    }
    finally {
      hold.finish()
    }
    startupManager.holdActivities().finish()
  }

  private suspend fun startupManager(): StartupManagerImpl = project.serviceAsync<StartupManager>() as StartupManagerImpl

  private class RecordingActivity : ProjectActivity {
    val ran = CompletableDeferred<Unit>()

    override suspend fun execute(project: Project) {
      ran.complete(Unit)
    }
  }

  @Suppress("UsagesOfObsoleteApi")
  private open class RecordingLegacyActivity : StartupActivity {
    val ran = CompletableDeferred<Unit>()

    override fun runActivity(project: Project) {
      ran.complete(Unit)
    }
  }

  private class RecordingLegacyDumbAwareActivity : RecordingLegacyActivity(), DumbAware
}
