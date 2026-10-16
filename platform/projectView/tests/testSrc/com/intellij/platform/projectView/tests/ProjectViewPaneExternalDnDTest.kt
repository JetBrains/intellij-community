// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.dnd.DnDAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.platform.projectView.pane.ProjectViewExternalFileDTO
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ProjectViewPaneExternalDnDTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewPaneExternalDnDTest::class.java, BLUEPRINT) }

    private val project = projectFixture(
      pathFixture = tempPathFixture(subdirName = "pvExternalDnD"),
      openAfterCreation = true,
    )
    private val module = project.moduleFixture(name = "pvExternalDnD")
    @Suppress("unused")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
    private val externalDir = tempPathFixture(subdirName = "external")
  }

  @Test
  fun `drop of an external file with the copy action copies it into the target directory`() = timeoutRunBlocking(60.seconds) {
    val project = project.get()
    val externalFile = externalDir.get().resolve("Dropped.txt").apply { writeText("dropped") }
    try {
      withProjectViewPane(project, ProjectPaneModel.ID) { pane ->
        val sub = pane.expand("pvExternalDnD", "src", "sub")

        pane.requestExternalDrop(listOf(ProjectViewExternalFileDTO.LocalPath(externalFile.toString())), sub, DnDAction.COPY)

        waitUntil("Dropped.txt should have been copied into src/sub", 30.seconds) {
          pane.dumpSubtree("pvExternalDnD", "src", "sub").lines().map { it.trim() }.contains("Dropped.txt")
        }
        assertTrue(externalFile.exists())
      }
    }
    finally {
      withContext(Dispatchers.EDT) {
        EditorHistoryManager.getInstance(project).removeAllFiles()
      }
    }
  }

  @Test
  fun `drop of an external file onto a file node copies it into the directory of the file`() = timeoutRunBlocking(60.seconds) {
    val project = project.get()
    val externalFile = externalDir.get().resolve("DroppedOnFile.txt").apply { writeText("dropped") }
    try {
      withProjectViewPane(project, ProjectPaneModel.ID) { pane ->
        val world = pane.expand("pvExternalDnD", "src", "sub", "World.txt")

        pane.requestExternalDrop(listOf(ProjectViewExternalFileDTO.LocalPath(externalFile.toString())), world, DnDAction.COPY)

        waitUntil("DroppedOnFile.txt should have been copied into src/sub", 30.seconds) {
          pane.dumpSubtree("pvExternalDnD", "src", "sub").lines().map { it.trim() }.contains("DroppedOnFile.txt")
        }
      }
    }
    finally {
      withContext(Dispatchers.EDT) {
        EditorHistoryManager.getInstance(project).removeAllFiles()
      }
    }
  }
}
