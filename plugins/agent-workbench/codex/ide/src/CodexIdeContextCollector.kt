// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.agent.workbench.prompt.context.AgentEditorContextSnapshot
import com.intellij.agent.workbench.prompt.context.AgentPromptEditorContextSupport
import com.intellij.agent.workbench.prompt.context.AgentPromptTextPosition
import com.intellij.agent.workbench.prompt.context.AgentPromptTextRange
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val MAX_OPEN_TABS = 500

internal class CodexIdeContextCollector(
    private val openProjectsProvider: () -> Array<Project> = { ProjectManager.getInstance().openProjects },
) {
    suspend fun collect(workspaceRoot: String): CodexIdeContext? {
        val root = parseWorkspaceRoot(workspaceRoot) ?: return null
        val projects = openProjectsProvider()
            .asSequence()
            .filter { project -> !project.isDisposed }
            .filter { project -> project.basePath?.let { basePath -> parsePath(basePath)?.let { overlaps(root, it) } } == true }
            .toList()
        if (projects.isEmpty()) {
            return null
        }

        return CodexIdeContext(
            activeFile = collectActiveFile(projects, root),
            openTabs = collectOpenTabs(projects, root),
        )
    }

    private suspend fun collectActiveFile(projects: List<Project>, workspaceRoot: Path): CodexIdeActiveFile? {
        for (project in projects) {
            val snapshot = AgentPromptEditorContextSupport.collectSelectedEditorSnapshot(project) ?: continue
            val virtualFile = snapshot.virtualFile ?: continue
            val descriptor = descriptorFor(virtualFile, workspaceRoot) ?: continue
            return CodexIdeActiveFile(
                label = descriptor.label,
                path = descriptor.path,
                fsPath = descriptor.fsPath,
                selection = snapshot.selection.toCodexRange(),
                activeSelectionContent = activeSelectionContent(snapshot),
                selections = snapshot.selections.map(AgentPromptTextRange::toCodexRange),
            )
        }
        return null
    }

    private suspend fun collectOpenTabs(projects: List<Project>, workspaceRoot: Path): List<CodexIdeFileDescriptor> {
        val openFiles = withContext(Dispatchers.EDT) {
            projects.flatMap { project ->
                if (project.isDisposed) {
                    emptyList()
                } else {
                    FileEditorManager.getInstance(project).openFiles.toList()
                }
            }
        }
        val result = LinkedHashMap<String, CodexIdeFileDescriptor>()
        for (file in openFiles) {
            val descriptor = descriptorFor(file, workspaceRoot) ?: continue
            result.putIfAbsent(descriptor.fsPath, descriptor)
            if (result.size >= MAX_OPEN_TABS) {
                break
            }
        }
        return result.values.toList()
    }

    private fun activeSelectionContent(snapshot: AgentEditorContextSnapshot): String {
        return if (snapshot.selections.size == 1 && snapshot.selection.start != snapshot.selection.end) {
            snapshot.activeSelectionContent
        } else {
            ""
        }
    }

    private fun descriptorFor(file: VirtualFile, workspaceRoot: Path): CodexIdeFileDescriptor? {
        if (file.isDirectory || isAgentWorkbenchChatVirtualFile(file)) {
            return null
        }
        val filePath = file.toNioPathOrNull()?.toAbsolutePath()?.normalize() ?: return null
        if (!filePath.startsWith(workspaceRoot)) {
            return null
        }
        val relativePath = workspaceRoot.relativize(filePath).invariantSeparatorsPathString
        if (relativePath.isBlank()) {
            return null
        }
        return CodexIdeFileDescriptor(
            label = file.name,
            path = relativePath,
            fsPath = filePath.invariantSeparatorsPathString,
        )
    }

    private fun parseWorkspaceRoot(workspaceRoot: String): Path? {
        return parsePath(workspaceRoot.trim())
    }

    private fun parsePath(path: String): Path? {
        return try {
            Path.of(path).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        }
    }

    private fun overlaps(first: Path, second: Path): Boolean {
        return first == second || first.startsWith(second) || second.startsWith(first)
    }

    private fun isAgentWorkbenchChatVirtualFile(file: VirtualFile): Boolean {
        return file.javaClass.name == "com.intellij.agent.workbench.chat.AgentChatVirtualFile"
    }
}

private fun AgentPromptTextRange.toCodexRange(): CodexIdeRange {
    return CodexIdeRange(start = start.toCodexPosition(), end = end.toCodexPosition())
}

private fun AgentPromptTextPosition.toCodexPosition(): CodexIdePosition {
    return CodexIdePosition(line = line, character = character)
}
