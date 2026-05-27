// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexIdeContextCollectorTest {
    private val projectFixture = projectFixture()
    private val project get() = projectFixture.get()
    private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

    @Test
    fun collectsActiveEditorAndOpenTabs(): Unit = timeoutRunBlocking(timeout = 60.seconds, context = Dispatchers.Default) {
        val projectRoot = Path.of(project.basePath!!)
        val activePath = writeProjectFile(projectRoot, "src/Main.kt", "fun main() {\n  println(42)\n}\n")
        val readmePath = writeProjectFile(projectRoot, "README.md", "Read me")
        withContext(Dispatchers.UiWithModelAccess) {
            val activeFile = activePath.refreshAndFindVirtualFile()!!
            val readmeFile = readmePath.refreshAndFindVirtualFile()!!
            val manager = fileEditorManagerFixture.get()
            manager.openFile(readmeFile, false)
            val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, activeFile), true)!!
            val selectionStart = editor.document.text.indexOf("println")
            editor.selectionModel.setSelection(selectionStart, selectionStart + "println(42)".length)
        }

        val context = CodexIdeContextCollector { arrayOf(project) }.collect(projectRoot.toString())!!

        assertThat(context.activeFile!!.label).isEqualTo("Main.kt")
        assertThat(context.activeFile.path).isEqualTo("src/Main.kt")
        assertThat(context.activeFile.fsPath).isEqualTo(activePath.toString())
        assertThat(context.activeFile.selection.start).isEqualTo(CodexIdePosition(line = 1, character = 2))
        assertThat(context.activeFile.selection.end).isEqualTo(CodexIdePosition(line = 1, character = 13))
        assertThat(context.activeFile.activeSelectionContent).isEqualTo("println(42)")
        assertThat(context.activeFile.selections).containsExactly(context.activeFile.selection)
        assertThat(context.openTabs.map { it.path }).containsExactlyInAnyOrder("src/Main.kt", "README.md")
    }

    @Test
    fun truncatesLargeActiveSelectionContent(): Unit = timeoutRunBlocking(timeout = 60.seconds, context = Dispatchers.Default) {
        val projectRoot = Path.of(project.basePath!!)
        val longSelection = "x".repeat(2_500)
        val activePath = writeProjectFile(projectRoot, "src/Large.txt", "$longSelection\nremaining")
        withContext(Dispatchers.UiWithModelAccess) {
            val activeFile = activePath.refreshAndFindVirtualFile()!!
            val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, activeFile), true)!!
            editor.selectionModel.setSelection(0, longSelection.length)
        }

        val context = CodexIdeContextCollector { arrayOf(project) }.collect(projectRoot.toString())!!

        assertThat(context.activeFile!!.activeSelectionContent.length).isLessThan(longSelection.length)
        assertThat(context.activeFile.activeSelectionContent).endsWith("\n...[truncated]")
        assertThat(context.activeFile.selection).isEqualTo(
            CodexIdeRange(
                start = CodexIdePosition(line = 0, character = 0),
                end = CodexIdePosition(line = 0, character = longSelection.length),
            )
        )
    }

    private fun writeProjectFile(projectRoot: Path, relativePath: String, content: String): Path {
        val path = projectRoot.resolve(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return path
    }
}
