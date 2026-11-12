// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class BuildViewProblemsServiceTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project by projectFixture

  @Test
  fun `concurrent file problems across build tasks are recorded without ConcurrentModificationException`(): Unit =
    timeoutRunBlocking(timeout = 30.seconds) {
      val service = project.service<BuildViewProblemsService>()
      val collector = ProblemsCollector.getInstance(project)
      val workingDir = "/tmp/test-project"
      val firstBuildId = Any()
      val secondBuildId = Any()
      val file = LightVirtualFile("Broken.java", "class Broken {}")
      val problemCount = 64
      val startSignal = CompletableDeferred<Unit>()

      try {
        service.onBuildStarted(workingDir, firstBuildId, collector)

        val jobs = (0 until problemCount).map { index ->
          launch(Dispatchers.Default) {
            startSignal.await()
            service.onFileBuildProblem(firstBuildId, TestFileProblem(service, file, "error $index", index), collector)
          }
        }
        startSignal.complete(Unit)
        jobs.joinAll()

        assertEquals(problemCount, collector.getFileProblems(file).count { it.provider === service })

        service.onBuildStarted(workingDir, secondBuildId, collector)
        assertEquals(0, collector.getFileProblems(file).count { it.provider === service })
      }
      finally {
        service.onBuildStarted(workingDir, Any(), collector)
      }
    }

  private data class TestFileProblem(
    override val provider: ProblemsProvider,
    override val file: VirtualFile,
    override val text: String,
    override val line: Int,
  ) : FileProblem
}
