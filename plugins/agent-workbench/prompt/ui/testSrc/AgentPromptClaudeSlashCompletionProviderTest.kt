// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.textCompletion.TextCompletionUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class AgentPromptClaudeSlashCompletionProviderTest {
    @Test
    fun completionIsDisabledForNonClaudeProviders() {
        val provider = createProvider(selectedProvider = { AgentSessionProvider.CODEX })

        assertThat(provider.getPrefix("/review", "/review".length)).isNull()
        assertThat(createProvider(selectedProvider = { null }).getPrefix("/review", "/review".length)).isNull()
    }

    @Test
    fun completionOnlyUsesSlashPrefixedWhitespaceDelimitedTokens() {
        val provider = createProvider(selectedProvider = { AgentSessionProvider.CLAUDE })

        assertThat(provider.getPrefix("please /safe-push", "please /safe-push".length)).isEqualTo("/safe-push")
        assertThat(provider.getPrefix("path/to/file", "path/to/file".length)).isNull()
        assertThat(provider.getPrefix("plain text", "plain text".length)).isNull()
    }

    @Test
    fun completionAutopopupAcceptsSlashCommandCharacters() {
        val provider = createProvider(selectedProvider = { AgentSessionProvider.CLAUDE })

        assertThat(provider.acceptChar('/')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
        assertThat(provider.acceptChar('a')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
        assertThat(provider.acceptChar('7')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
        assertThat(provider.acceptChar('-')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
        assertThat(provider.acceptChar('_')).isEqualTo(CharFilter.Result.ADD_TO_PREFIX)
        assertThat(provider.acceptChar(' ')).isNull()
    }

    @Test
    fun autoPopupOnlyTriggersForLeadingSlashTypedAsFirstPromptCharacter(@TempDir tempDir: Path) {
        val projectPath = tempDir.resolve("project")
        Files.createDirectories(projectPath)
        writeCommand(projectPath, "review")

        assertThat(
            shouldAutoPopupClaudeSlashCompletion(
                selectedProvider = AgentSessionProvider.CLAUDE,
                workingProjectPaths = listOf(projectPath.toString()),
                text = "/",
                offsetAfterChange = 1,
                insertedFragment = "/",
            ),
        ).isTrue()

        assertThat(
            shouldAutoPopupClaudeSlashCompletion(
                selectedProvider = AgentSessionProvider.CLAUDE,
                workingProjectPaths = listOf(projectPath.toString()),
                text = "open /",
                offsetAfterChange = "open /".length,
                insertedFragment = "/",
            ),
        ).isFalse()

        assertThat(
            shouldAutoPopupClaudeSlashCompletion(
                selectedProvider = AgentSessionProvider.CLAUDE,
                workingProjectPaths = listOf(projectPath.toString()),
                text = "/review",
                offsetAfterChange = "/review".length,
                insertedFragment = "review",
            ),
        ).isFalse()

        assertThat(
            shouldAutoPopupClaudeSlashCompletion(
                selectedProvider = AgentSessionProvider.CODEX,
                workingProjectPaths = listOf(projectPath.toString()),
                text = "/",
                offsetAfterChange = 1,
                insertedFragment = "/",
            ),
        ).isFalse()
    }

    @Test
    fun completionPathFallsBackToProjectBasePath() {
        assertThat(
            resolveClaudeSlashCompletionProjectPaths(
                workingProjectPath = null,
                sourceProjectBasePath = null,
                projectBasePath = "/repo"
            )
        )
            .containsExactly("/repo")
        assertThat(
            resolveClaudeSlashCompletionProjectPaths(
                workingProjectPath = "/workspace",
                sourceProjectBasePath = null,
                projectBasePath = "/repo"
            )
        )
            .containsExactly("/workspace", "/repo")
        assertThat(
            resolveClaudeSlashCompletionProjectPaths(
                workingProjectPath = null,
                sourceProjectBasePath = "/source",
                projectBasePath = "/dedicated"
            )
        )
            .containsExactly("/source", "/dedicated")
    }

    @Test
    fun includesBuiltInClaudeMenuEntries() {
        val entries = collectClaudeSlashCompletionEntries(emptyList<String>())

        assertThat(entries.filter { entry -> entry.kind == AgentPromptClaudeSlashCompletionKind.MENU }.map { entry -> entry.lookupString })
            .contains("/mcp", "/memory", "/model")
        assertThat(entries.single { entry -> entry.lookupString == "/compact" }.argumentHint).isEqualTo("[instructions]")
        assertThat(entries.single { entry -> entry.lookupString == "/model" }.argumentHint).isEqualTo("[model]")
        assertThat(entries.single { entry -> entry.lookupString == "/resume" }.argumentHint).isEqualTo("[session]")
    }

    @Test
    fun invokingBasicCompletionOnPromptFieldShowsClaudeSlashEntries(@TempDir tempDir: Path) {
        val projectPath = tempDir.resolve("project")
        Files.createDirectories(projectPath)
        writeCommand(projectPath, "review", argumentHint = "[PR number]")
        writeSkill(projectPath, "safe-push", argumentHint = "[focus]")

        runInEdtAndWait {
            val project = ProjectManager.getInstance().defaultProject
            val completionProvider = createProvider(
                selectedProvider = { AgentSessionProvider.CLAUDE },
                workingProjectPaths = { listOf(projectPath.toString()) },
            )
            val textField = AgentPromptTextField(project, completionProvider)
            textField.addNotify()
            try {
                textField.text = "/"
                val editor = checkNotNull(textField.editor) { "Editor was not initialized" }
                editor.caretModel.moveToOffset(1)

                CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true).invokeCompletion(project, editor, 1)

                val lookup = LookupManager.getActiveLookup(editor)
                assertThat(lookup).isNotNull
                val activeLookup = checkNotNull(lookup)
                assertThat(activeLookup.items.map { item -> item.lookupString }).contains("/mcp")
                assertThat(activeLookup.items.map { item -> item.lookupString }).contains("/review")
                assertThat(activeLookup.items.map { item -> item.lookupString }).contains("/safe-push")

                val tailTextByLookup = activeLookup.items.associate { item ->
                    item.lookupString to LookupElementPresentation.renderElement(item).tailText
                }
                assertThat(tailTextByLookup["/model"]).isEqualTo(" [model]")
                assertThat(tailTextByLookup["/review"]).isEqualTo(" [PR number]")
                assertThat(tailTextByLookup["/safe-push"]).isEqualTo(" [focus]")
            } finally {
                textField.removeNotify()
            }
        }
    }

    @Test
    fun collectsEntriesFromClaudeAncestorChain(@TempDir tempDir: Path) {
        val workspaceRoot = tempDir.resolve("workspace")
        val projectPath = workspaceRoot.resolve("project")
        Files.createDirectories(projectPath)
        writeCommand(tempDir, "repair")
        writeSkill(workspaceRoot, "safe-push")

        val entries = collectClaudeSlashCompletionEntries(projectPath.toString())

        assertThat(entries.filter { entry -> entry.kind != AgentPromptClaudeSlashCompletionKind.MENU }
            .map(AgentPromptClaudeSlashCompletionEntry::lookupString))
            .containsExactly("/repair", "/safe-push")
    }

    @Test
    fun nearerAncestorOverridesSameKindEntries(@TempDir tempDir: Path) {
        val workspaceRoot = tempDir.resolve("workspace")
        val projectPath = workspaceRoot.resolve("project")
        Files.createDirectories(projectPath)
        val parentCommand = writeCommand(tempDir, "review")
        val nearerCommand = writeCommand(workspaceRoot, "review")

        val entries = collectClaudeSlashCompletionEntries(projectPath.toString())
            .filter { entry -> entry.kind == AgentPromptClaudeSlashCompletionKind.COMMAND && entry.name == "review" }
        val entry = entries.single()

        assertThat(entry.lookupString).isEqualTo("/review")
        assertThat(entry.kind).isEqualTo(AgentPromptClaudeSlashCompletionKind.COMMAND)
        assertThat(entry.sourcePath).isEqualTo(nearerCommand)
        assertThat(entry.sourcePath).isNotEqualTo(parentCommand)
    }

    @Test
    fun sameNameCommandAndSkillAreBothReturnedWithCommandFirst(@TempDir tempDir: Path) {
        val projectPath = tempDir.resolve("project")
        Files.createDirectories(projectPath)
        writeCommand(tempDir, "review")
        writeSkill(tempDir, "review")

        val entries = collectClaudeSlashCompletionEntries(projectPath.toString()).filter { entry -> entry.name == "review" }

        assertThat(entries.map(AgentPromptClaudeSlashCompletionEntry::kind))
            .containsExactly(AgentPromptClaudeSlashCompletionKind.COMMAND, AgentPromptClaudeSlashCompletionKind.SKILL)
    }

    @Test
    fun collectsArgumentHintsFromCommandAndSkillFrontmatter(@TempDir tempDir: Path) {
        val projectPath = tempDir.resolve("project")
        Files.createDirectories(projectPath)
        writeCommand(tempDir, "review", argumentHint = "[PR number]")
        writeSkill(tempDir, "safe-push", argumentHint = "[focus]")

        val entriesByLookup =
            collectClaudeSlashCompletionEntries(projectPath.toString()).associateBy(AgentPromptClaudeSlashCompletionEntry::lookupString)

        assertThat(entriesByLookup["/review"]?.argumentHint).isEqualTo("[PR number]")
        assertThat(entriesByLookup["/safe-push"]?.argumentHint).isEqualTo("[focus]")
    }

    @Test
    fun onlyImmediateClaudeLocationsAreRecognized(@TempDir tempDir: Path) {
        val projectPath = tempDir.resolve("project")
        Files.createDirectories(projectPath)
        writeCommand(tempDir, "review")
        Files.createDirectories(tempDir.resolve(".claude").resolve("commands").resolve("nested"))
        Files.writeString(tempDir.resolve(".claude").resolve("commands").resolve("nested").resolve("ignored.md"), "# nested")
        Files.createDirectories(tempDir.resolve(".claude").resolve("skills").resolve("safe-push").resolve("nested"))
        Files.writeString(
            tempDir.resolve(".claude").resolve("skills").resolve("safe-push").resolve("nested").resolve("SKILL.md"),
            "# nested"
        )

        val entries = collectClaudeSlashCompletionEntries(projectPath.toString())

        assertThat(entries.filter { entry -> entry.kind != AgentPromptClaudeSlashCompletionKind.MENU }
            .map(AgentPromptClaudeSlashCompletionEntry::lookupString))
            .containsExactly("/review")
    }

    @Test
    fun textFieldUsesTextDocumentAndKeepsCompletionProviderInstalled() {
        runInEdtAndWait {
            val project = ProjectManager.getInstance().defaultProject
            val completionProvider = createProvider(selectedProvider = { AgentSessionProvider.CLAUDE })
            val textField = AgentPromptTextField(project, completionProvider)

            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(textField.document)

            assertThat(psiFile).isNotNull
            assertThat(psiFile!!.fileType).isEqualTo(FileTypes.PLAIN_TEXT)
            assertThat(TextCompletionUtil.getProvider(psiFile)).isSameAs(completionProvider)
        }
    }

    private fun createProvider(
        selectedProvider: () -> AgentSessionProvider?,
        workingProjectPaths: () -> List<String> = { emptyList() },
    ): AgentPromptClaudeSlashCompletionProvider {
        return AgentPromptClaudeSlashCompletionProvider(selectedProvider, workingProjectPaths)
    }

    private fun writeCommand(root: Path, name: String, argumentHint: String? = null): Path {
        val commandFile = root.resolve(".claude").resolve("commands").resolve("$name.md")
        Files.createDirectories(commandFile.parent)
        Files.writeString(commandFile, buildSlashCompletionContent(name, argumentHint))
        return commandFile
    }

    private fun writeSkill(root: Path, name: String, argumentHint: String? = null): Path {
        val skillFile = root.resolve(".claude").resolve("skills").resolve(name).resolve("SKILL.md")
        Files.createDirectories(skillFile.parent)
        Files.writeString(skillFile, buildSlashCompletionContent(name, argumentHint))
        return skillFile
    }

    private fun buildSlashCompletionContent(name: String, argumentHint: String?): String {
        return buildString {
            if (!argumentHint.isNullOrBlank()) {
                appendLine("---")
                appendLine("argument-hint: $argumentHint")
                appendLine("---")
                appendLine()
            }
            append("# $name")
        }
    }
}
