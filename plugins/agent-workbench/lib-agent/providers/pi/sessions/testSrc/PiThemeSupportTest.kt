// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.util.io.DigestUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiThemeSupportTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun materializesBundledExtensionManifestAndCurrentThemeState() {
    val support = supportFor(
      bundledExtension("extension-v1", "helper.ts" to "helper-v1"),
      themeSnapshotProvider = { snapshot("islands-dark", "Islands Dark", dark = true) },
    )

    val resources = support.launchResourcesOrNull()

    assertThat(resources).isEqualTo(
      PiExtensionLaunchResources(
        extensionPath = tempDir.resolve("extension").resolve(extensionFileName("extension-v1")),
        stateFilePath = tempDir.resolve("state").resolve("current-theme.txt"),
      )
    )
    assertThat(Files.readString(resources!!.extensionPath)).isEqualTo("extension-v1")
    assertThat(Files.readString(tempDir.resolve("extension").resolve("helper.ts"))).isEqualTo("helper-v1")
    assertThat(Files.readString(resources.stateFilePath))
      .contains("\"themeId\":\"islands-dark\"", "\"dark\":true")
    assertThat(tempDir.resolve(".awb-theme-manifest")).exists()
    assertThat(Files.readString(tempDir.resolve(".awb-theme-manifest")))
      .isEqualTo(
        """
          formatVersion=4
          ${extensionFileName("extension-v1")}=${DigestUtil.sha256Hex(bytes("extension-v1"))}
          helper.ts=${DigestUtil.sha256Hex(bytes("helper-v1"))}
        """.trimIndent() + "\n"
      )
  }

  @Test
  fun rewritesStaleMaterializedExtensionWhenBundledHashChanges() {
    supportFor(
      singleFileBundledExtension("extension-v1"),
    ).launchResourcesOrNull()

    supportFor(
      singleFileBundledExtension("extension-v2"),
    ).launchResourcesOrNull()

    assertThat(tempDir.resolve("extension").resolve(extensionFileName("extension-v1"))).doesNotExist()
    assertThat(Files.readString(tempDir.resolve("extension").resolve(extensionFileName("extension-v2")))).isEqualTo("extension-v2")
    assertThat(Files.readString(tempDir.resolve(".awb-theme-manifest")))
      .contains("${extensionFileName("extension-v2")}=${DigestUtil.sha256Hex(bytes("extension-v2"))}")
  }

  @Test
  fun rewritesStableMaterializedExtensionModuleWhenBundledHashChanges() {
    supportFor(
      bundledExtension("extension-v1", "helper.ts" to "helper-v1"),
    ).launchResourcesOrNull()

    supportFor(
      bundledExtension("extension-v1", "helper.ts" to "helper-v2"),
    ).launchResourcesOrNull()

    assertThat(Files.readString(tempDir.resolve("extension").resolve(extensionFileName("extension-v1")))).isEqualTo("extension-v1")
    assertThat(Files.readString(tempDir.resolve("extension").resolve("helper.ts"))).isEqualTo("helper-v2")
    assertThat(Files.readString(tempDir.resolve(".awb-theme-manifest")))
      .contains("helper.ts=${DigestUtil.sha256Hex(bytes("helper-v2"))}")
  }

  @Test
  fun removesUnexpectedFilesFromManagedExtensionDirectory() {
    val extensionDirectory = tempDir.resolve("extension")
    Files.createDirectories(extensionDirectory)
    Files.writeString(extensionDirectory.resolve("old.ts"), "stale")

    supportFor(
      singleFileBundledExtension("extension-v1"),
    ).launchResourcesOrNull()

    assertThat(extensionDirectory.resolve("old.ts")).doesNotExist()
    assertThat(extensionDirectory.resolve(extensionFileName("extension-v1"))).exists()
  }

  @Test
  fun removesLegacyThemeFilesWhenManifestFormatChanges() {
    val legacyThemeDirectory = tempDir.resolve("themes")
    Files.createDirectories(legacyThemeDirectory)
    Files.writeString(legacyThemeDirectory.resolve("dark.json"), "stale")
    Files.writeString(tempDir.resolve(".awb-theme-manifest"), "formatVersion=1\ndark.json=stale\n")

    supportFor(
      singleFileBundledExtension("extension-v1"),
    ).launchResourcesOrNull()

    assertThat(legacyThemeDirectory.resolve("dark.json")).doesNotExist()
  }

  @Test
  fun cachesMaterializedExtensionAfterFirstSuccessAndRefreshesThemeState() {
    var loadCount = 0
    var themeId = "islands-dark"
    val support = PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = {
        loadCount++
        singleFileBundledExtension("extension-v1")
      },
      themeSnapshotProvider = { snapshot(themeId, themeId, dark = themeId != "islands-light") },
    )

    val first = support.launchResourcesOrNull()
    themeId = "islands-light"
    val second = support.launchResourcesOrNull()

    assertThat(first).isEqualTo(
      PiExtensionLaunchResources(
        tempDir.resolve("extension").resolve(extensionFileName("extension-v1")),
        tempDir.resolve("state").resolve("current-theme.txt"),
      )
    )
    assertThat(second).isEqualTo(first)
    assertThat(loadCount).isEqualTo(1)
    assertThat(Files.readString(second!!.stateFilePath)).contains("\"themeId\":\"islands-light\"")
  }

  @Test
  fun repairsCachedExtensionWhenMaterializedFileIsMissingOrTamperedWith() {
    val support = supportFor(
      singleFileBundledExtension("extension-v1"),
    )
    val first = support.launchResourcesOrNull()

    Files.delete(first!!.extensionPath)
    val second = support.launchResourcesOrNull()

    assertThat(second).isEqualTo(first)
    assertThat(Files.readString(first.extensionPath)).isEqualTo("extension-v1")

    Files.writeString(first.extensionPath, "tampered")
    val third = support.launchResourcesOrNull()

    assertThat(third).isEqualTo(first)
    assertThat(Files.readString(first.extensionPath)).isEqualTo("extension-v1")
  }

  @Test
  fun syncCurrentThemeStateUsesMaterializedStateFile() {
    var themeId = "islands-dark"
    val support = supportFor(
      singleFileBundledExtension("extension-v1"),
      themeSnapshotProvider = { snapshot(themeId, themeId, dark = themeId != "islands-light") },
    )
    val resources = support.launchResourcesOrNull()

    themeId = "islands-light"
    support.syncCurrentThemeState()

    assertThat(Files.readString(resources!!.stateFilePath)).contains("\"themeId\":\"islands-light\"")
  }

  @Test
  fun returnsNullWhenMaterializationFails() {
    val support = PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = { error("missing extension") },
    )

    assertThat(support.launchResourcesOrNull()).isNull()
  }

  @Test
  fun bundledExtensionMarksJbCentralClaudeOpus48AsAdaptiveThinkingModel() {
    val extension = readBundledPiExtensionText("jbcentral.ts")

    assertThat(extension).contains(
      "JBCENTRAL_ADAPTIVE_THINKING_MODEL_MARKERS",
      "\"opus-4-8\"",
      "\"opus-4.8\"",
      "...(isJbCentralAdaptiveThinkingModel(model) ? {compat: {forceAdaptiveThinking: true}} : {})",
    )
  }

  @Test
  fun bundledExtensionEntrypointImportsSplitModules() {
    val extension = readBundledPiExtensionText("agent-workbench-extension.ts")

    assertThat(extension).contains(
      "from \"./control.ts\"",
      "from \"./jbcentral.ts\"",
      "from \"./omlx.ts\"",
      "from \"./status.ts\"",
      "from \"./taskFolders.ts\"",
      "from \"./theme.ts\"",
      "controlBridge = startControlBridge(ctx)",
    )
  }

  @Test
  fun bundledExtensionRegistersTaskFolderToolForPi() {
    val entrypoint = readBundledPiExtensionText("agent-workbench-extension.ts")
    val taskFolders = readBundledPiExtensionText("taskFolders.ts")

    assertThat(entrypoint).contains(
      "import {startControlBridge} from \"./control.ts\";",
      "import {registerTaskFolderTools} from \"./taskFolders.ts\";",
      "registerTaskFolderTools(pi, () => controlBridge);",
    )
    assertThat(taskFolders).contains(
      "const TASK_FOLDER_TOOL_DEFINITIONS",
      "pi.registerTool(defineTool({",
      "const bridge = bridgeProvider();",
      "if (bridge === undefined)",
      "name: \"agent_workbench_get_current_task_folder\"",
      "name: \"agent_workbench_list_task_folders\"",
      "name: \"agent_workbench_list_project_threads\"",
      "name: \"agent_workbench_create_task_folder\"",
      "name: \"agent_workbench_move_thread_to_task_folder\"",
      "name: \"agent_workbench_remove_thread_from_task_folder\"",
      "name: \"agent_workbench_set_task_folder_metadata\"",
      "name: \"agent_workbench_mark_task_folder_done\"",
      "name: \"agent_workbench_delete_task_folder\"",
      "parameters: Type.Object({",
      "name: Type.String({description: \"Task folder name\"})",
      "metadata: Type.Optional(Type.Record(Type.String(), Type.String({description: TASK_FOLDER_METADATA_DESCRIPTION})))",
      "assignCurrentThread: Type.Optional(Type.Boolean({description: \"Whether to assign the current Pi thread; defaults to true\"}))",
      "provider: Type.String({description: \"Agent Workbench provider id, for example pi, codex, claude, or junie\"})",
      "threadId: Type.String({description: \"Agent Workbench thread id\"})",
      "Use metadata key 'issue' for issue tracker ids and 'review' for review ids; do not use separate issue parameters.",
      "const result = await bridge.request(TASK_FOLDER_REQUEST_TYPE, {operation: definition.operation, arguments: args});",
      "formatProjectThreads(resultProjectThreads(result))",
      "formatFolder(folder)",
      "details: result",
    )
  }

  private fun supportFor(
    extension: PiBundledExtensionResources,
    themeSnapshotProvider: () -> PiThemeSnapshot = { snapshot("islands-dark", "Islands Dark", dark = true) },
  ): PiThemeSupport {
    return PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = { extension },
      themeSnapshotProvider = themeSnapshotProvider,
    )
  }

  private fun bytes(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)

  private fun singleFileBundledExtension(entryText: String): PiBundledExtensionResources {
    return bundledExtension(entryText)
  }

  private fun bundledExtension(entryText: String, vararg extraFiles: Pair<String, String>): PiBundledExtensionResources {
    val entryFileName = "agent-workbench-extension.ts"
    return PiBundledExtensionResources(
      entryFileName = entryFileName,
      files = listOf(PiBundledExtensionResource(entryFileName, bytes(entryText))) +
              extraFiles.map { (fileName, text) -> PiBundledExtensionResource(fileName, bytes(text)) },
    )
  }

  private fun readBundledPiExtensionText(fileName: String): String {
    val resourcePath = "pi-extension/$fileName"
    val bytes = PiThemeSupport::class.java.classLoader.getResourceAsStream(resourcePath)
                  ?.use { input -> input.readBytes() }
                ?: error("Missing bundled Pi extension resource: $resourcePath")
    return String(bytes, StandardCharsets.UTF_8)
  }

  private fun extensionFileName(text: String): String {
    return "agent-workbench-extension-${DigestUtil.sha256Hex(bytes(text)).take(16)}.ts"
  }
}

private fun snapshot(themeId: String, themeName: String, dark: Boolean): PiThemeSnapshot {
  return PiThemeSnapshot(
    formatVersion = PI_THEME_STATE_FORMAT_VERSION,
    themeId = themeId,
    themeName = themeName,
    dark = dark,
    fg = PI_THEME_TEST_FG_KEYS.associateWith { "#111111" },
    bg = PI_THEME_TEST_BG_KEYS.associateWith { "#222222" },
  )
}

internal val PI_THEME_TEST_FG_KEYS = listOf(
  "accent",
  "border",
  "borderAccent",
  "borderMuted",
  "success",
  "error",
  "warning",
  "muted",
  "dim",
  "text",
  "thinkingText",
  "userMessageText",
  "customMessageText",
  "customMessageLabel",
  "toolTitle",
  "toolOutput",
  "mdHeading",
  "mdLink",
  "mdLinkUrl",
  "mdCode",
  "mdCodeBlock",
  "mdCodeBlockBorder",
  "mdQuote",
  "mdQuoteBorder",
  "mdHr",
  "mdListBullet",
  "toolDiffAdded",
  "toolDiffRemoved",
  "toolDiffContext",
  "syntaxComment",
  "syntaxKeyword",
  "syntaxFunction",
  "syntaxVariable",
  "syntaxString",
  "syntaxNumber",
  "syntaxType",
  "syntaxOperator",
  "syntaxPunctuation",
  "thinkingOff",
  "thinkingMinimal",
  "thinkingLow",
  "thinkingMedium",
  "thinkingHigh",
  "thinkingXhigh",
  "bashMode",
)

internal val PI_THEME_TEST_BG_KEYS = listOf(
  "selectedBg",
  "userMessageBg",
  "customMessageBg",
  "toolPendingBg",
  "toolSuccessBg",
  "toolErrorBg",
)
