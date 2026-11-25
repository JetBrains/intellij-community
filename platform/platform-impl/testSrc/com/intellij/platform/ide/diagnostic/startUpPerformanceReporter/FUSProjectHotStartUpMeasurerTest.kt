// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.ProjectsType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class FUSProjectHotStartUpMeasurerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    FUSProjectHotStartUpMeasurer.resetForTest()
  }

  @AfterEach
  fun tearDown() {
    FUSProjectHotStartUpMeasurer.resetForTest()
  }

  @Test
  fun `multi-project startup waits for the second project when the first project finishes earlier`(@TempDir tempDir: Path) {
    val project1Dir = createProjectDir(tempDir, "project1", withSettings = true)
    val project2Dir = createProjectDir(tempDir, "project2", withSettings = false)

    val events = collectStartupEvents {
      val starter = checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(close = false))
      withContext(starter) {
        FUSProjectHotStartUpMeasurer.reportProjectType(ProjectsType.FromArgs)
        FUSProjectHotStartUpMeasurer.openingMultipleProjects(reopening = false, numberOfProjects = 2, hasLightEditProjects = false)
        val collectorJob = launch {
          FUSProjectHotStartUpMeasurer.startWritingStatistics()
        }

        FUSProjectHotStartUpMeasurer.withProjectContextElement(project1Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }

        delay(30.milliseconds)
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isFalse()

        FUSProjectHotStartUpMeasurer.withProjectContextElement(project2Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }

        collectorJob.join()
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isTrue()
      }
    }

    assertMultiProjectEvents(
      events = events,
      expectedProjectsType = "FromArgs",
      expectedHasSettingsByOrder = mapOf(1L to true, 2L to false),
      expectedNoEditorsByOrder = mapOf(1L to true, 2L to true),
    )
  }

  @Test
  fun `multi-project startup waits for frame interactive events when editor completion arrives first`(@TempDir tempDir: Path) {
    val project1Dir = createProjectDir(tempDir, "project1", withSettings = true)
    val project2Dir = createProjectDir(tempDir, "project2", withSettings = true)

    val events = collectStartupEvents {
      val starter = checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(close = false))
      withContext(starter) {
        FUSProjectHotStartUpMeasurer.reportReopeningProjects(listOf(project1Dir, project2Dir))
        val collectorJob = launch {
          FUSProjectHotStartUpMeasurer.startWritingStatistics()
        }

        val project1Context = FUSProjectHotStartUpMeasurer.withProjectContextElement(project1Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass())
        }
        val project2Context = FUSProjectHotStartUpMeasurer.withProjectContextElement(project2Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass())
        }

        withContext(project1Context) {
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }
        withContext(project2Context) {
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }

        delay(30.milliseconds)
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isFalse()

        withContext(project1Context) {
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
        }
        delay(30.milliseconds)
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isFalse()

        withContext(project2Context) {
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
        }

        collectorJob.join()
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isTrue()
      }
    }

    assertMultiProjectEvents(
      events = events,
      expectedProjectsType = "Reopened",
      expectedHasSettingsByOrder = mapOf(1L to true, 2L to true),
      expectedNoEditorsByOrder = mapOf(1L to true, 2L to true),
    )
  }

  @Test
  fun `multi-project startup keeps monotonic durations when frame interactive arrives after editor event`(@TempDir tempDir: Path) {
    val project1Dir = createProjectDir(tempDir, "project1", withSettings = true)
    val project2Dir = createProjectDir(tempDir, "project2", withSettings = true)
    val readmeFile = LightVirtualFile("README.md", "# Demo")

    val events = collectStartupEvents {
      val starter = checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(close = false))
      withContext(starter) {
        FUSProjectHotStartUpMeasurer.reportProjectType(ProjectsType.FromArgs)
        FUSProjectHotStartUpMeasurer.openingMultipleProjects(reopening = false, numberOfProjects = 2, hasLightEditProjects = false)

        val project1Context = FUSProjectHotStartUpMeasurer.withProjectContextElement(project1Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass())
        }
        val project2Context = FUSProjectHotStartUpMeasurer.withProjectContextElement(project2Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass())
        }

        withContext(project1Context) {
          FUSProjectHotStartUpMeasurer.openedReadme(readmeFile, System.nanoTime())
        }
        delay(30.milliseconds)
        withContext(project1Context) {
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
        }
        withContext(project2Context) {
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }

        FUSProjectHotStartUpMeasurer.startWritingStatistics()
      }
    }

    assertMultiProjectEvents(
      events = events,
      expectedProjectsType = "FromArgs",
      expectedHasSettingsByOrder = mapOf(1L to true, 2L to true),
      expectedNoEditorsByOrder = mapOf(1L to false, 2L to true),
    )
    val project1CodeLoaded = events.single {
      it.event.id == "multiple.project.code.loaded.and.visible.in.editor" &&
      (it.event.data["project_order"] as Number).toLong() == 1L
    }
    assertThat(project1CodeLoaded.event.data["source_of_selected_editor"]).isEqualTo("FoundReadmeFile")
  }

  @Test
  fun `multi-project startup orders frame visible before frame interactive when early frame reports interactive first`(
    @TempDir tempDir: Path,
  ) {
    val project1Dir = createProjectDir(tempDir, "project1", withSettings = true)
    val project2Dir = createProjectDir(tempDir, "project2", withSettings = true)

    val events = collectStartupEvents {
      val starter = checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(close = false))
      withContext(starter) {
        FUSProjectHotStartUpMeasurer.reportProjectType(ProjectsType.FromArgs)
        FUSProjectHotStartUpMeasurer.openingMultipleProjects(reopening = false, numberOfProjects = 2, hasLightEditProjects = false)

        val shownEarlyAt = System.nanoTime()
        delay(25.milliseconds)
        FUSProjectHotStartUpMeasurer.withProjectContextElement(project1Dir) {
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
          FUSProjectHotStartUpMeasurer.frameBecameVisible(shownEarlyAt)
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }
        FUSProjectHotStartUpMeasurer.withProjectContextElement(project2Dir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }

        FUSProjectHotStartUpMeasurer.startWritingStatistics()
      }
    }

    assertMultiProjectEvents(
      events = events,
      expectedProjectsType = "FromArgs",
      expectedHasSettingsByOrder = mapOf(1L to true, 2L to true),
      expectedNoEditorsByOrder = mapOf(1L to true, 2L to true),
    )
  }

  @Test
  fun `single-project startup waits for frame interactive event when editor completion arrives first`(@TempDir tempDir: Path) {
    val projectDir = createProjectDir(tempDir, "project1", withSettings = true)

    val events = collectStartupEvents {
      val starter = checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(close = false))
      withContext(starter) {
        FUSProjectHotStartUpMeasurer.reportProjectType(ProjectsType.FromArgs)
        val collectorJob = launch {
          FUSProjectHotStartUpMeasurer.startWritingStatistics()
        }

        val projectContext = FUSProjectHotStartUpMeasurer.withProjectContextElement(projectDir) {
          FUSProjectHotStartUpMeasurer.frameBecameVisible()
          checkNotNull(FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass())
        }
        withContext(projectContext) {
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
        }
        delay(25.milliseconds)
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isFalse()

        withContext(projectContext) {
          FUSProjectHotStartUpMeasurer.reportFrameBecameInteractive()
        }
        collectorJob.join()
        assertThat(FUSProjectHotStartUpMeasurer.isHandlingFinished()).isTrue()
      }
    }

    assertThat(events.map { it.event.id }).containsExactly(
      "first.ui.shown",
      "frame.became.visible",
      "frame.became.interactive",
      "code.loaded.and.visible.in.editor",
    )
    val durations = events.map { (it.event.data["duration_ms"] as Number).toLong() }
    assertThat(durations[0]).isEqualTo(durations[1])
    assertThat(durations[1]).isLessThanOrEqualTo(durations[2])
    assertThat(durations[2]).isLessThanOrEqualTo(durations[3])
  }

  private fun createProjectDir(tempDir: Path, name: String, withSettings: Boolean): Path {
    val dir = tempDir.resolve(name).createDirectories()
    if (withSettings) {
      dir.resolve(".idea").createDirectories()
    }
    return dir
  }

  private fun collectStartupEvents(block: suspend () -> Unit): List<LogEvent> {
    return FUCollectorTestCase.collectLogEvents(disposable) {
      runBlocking {
        withTimeout(10.seconds) {
          block()
        }
      }
    }.filter { it.group.id == "reopen.project.startup.performance" }
  }

  private fun assertMultiProjectEvents(
    events: List<LogEvent>,
    expectedProjectsType: String,
    expectedHasSettingsByOrder: Map<Long, Boolean>,
    expectedNoEditorsByOrder: Map<Long, Boolean>,
  ) {
    val singleEvents = events.filterNot { it.event.id.startsWith("multiple.project.") }
    assertThat(singleEvents.map { it.event.id }).containsExactly("first.ui.shown", "frame.became.visible")
    assertThat(singleEvents[0].event.data["type"]).isEqualTo("Frame")
    assertThat(singleEvents[1].event.data["violation"]).isEqualTo("MultipleProjects")

    val byProjectOrder = events
      .filter { it.event.id.startsWith("multiple.project.") }
      .groupBy { (it.event.data["project_order"] as Number).toLong() }

    assertThat(byProjectOrder.keys).containsExactlyInAnyOrder(1L, 2L)
    for (projectOrder in listOf(1L, 2L)) {
      val projectEvents = byProjectOrder.getValue(projectOrder)
      assertThat(projectEvents.map { it.event.id }).containsExactly(
        "multiple.project.frame.became.visible",
        "multiple.project.frame.became.interactive",
        "multiple.project.code.loaded.and.visible.in.editor",
      )

      val visibleData = projectEvents[0].event.data
      val interactiveData = projectEvents[1].event.data
      val codeLoadedData = projectEvents[2].event.data

      assertThat(visibleData["projects_type"]).isEqualTo(expectedProjectsType)
      assertThat(visibleData["has_settings"]).isEqualTo(expectedHasSettingsByOrder.getValue(projectOrder))
      assertThat((visibleData["number_of_projects"] as Number).toLong()).isEqualTo(2L)

      assertThat((interactiveData["number_of_projects"] as Number).toLong()).isEqualTo(2L)

      assertThat(codeLoadedData["has_settings"]).isEqualTo(expectedHasSettingsByOrder.getValue(projectOrder))
      assertThat(codeLoadedData["no_editors_to_open"]).isEqualTo(expectedNoEditorsByOrder.getValue(projectOrder))
      assertThat((codeLoadedData["number_of_projects"] as Number).toLong()).isEqualTo(2L)

      val visibleDuration = (visibleData["duration_ms"] as Number).toLong()
      val interactiveDuration = (interactiveData["duration_ms"] as Number).toLong()
      val codeLoadedDuration = (codeLoadedData["duration_ms"] as Number).toLong()
      assertThat(visibleDuration).isLessThanOrEqualTo(interactiveDuration)
      assertThat(interactiveDuration).isLessThanOrEqualTo(codeLoadedDuration)
    }
  }
}
