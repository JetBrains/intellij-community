// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// A write reaches the vault at once, but it reaches the disk after a debounce. A push and an external edit arrive
// asynchronously. So a test polls for a change, and these helpers hold that wait.

/**
 * Holds the longest time that a poll waits for a change. The persist debounce is milliseconds, so this covers the
 * background work of the whole suite: indexing, a refresh and the reload of another project all share the machine.
 */
private val DISK_TIMEOUT = 30.seconds

/**
 * Polls [read] until it equals [expected], for up to [DISK_TIMEOUT]. Then asserts the equality, so a real mismatch
 * still reports.
 */
internal suspend fun <T> awaitEquals(expected: T, read: () -> T) {
  withTimeout(DISK_TIMEOUT) {
    while (read() != expected) delay(10.milliseconds)
  }
  assertEquals(expected, read())
}

/**
 * Returns the file name of the [locality] variant of the team config [teamFileName]. It mirrors `localityFileName` in
 * `shared/src/com/intellij/platform/settings/v2/impl/providers/localityFiles.kt`.
 */
internal fun localityConfigName(teamFileName: String, locality: LayerLocality): String {
  val stem = teamFileName.removeSuffix(".json")
  return when (locality) {
    LayerLocality.TEAM -> teamFileName
    LayerLocality.LOCAL -> "$stem.local.json"
    LayerLocality.ENVIRONMENTAL -> "$stem.env.json"
  }
}

/** Returns the `.local.json` sibling of the team config [teamFileName]. A plain write lands there, except at the application. */
internal fun localConfigName(teamFileName: String): String = localityConfigName(teamFileName, LayerLocality.LOCAL)

/**
 * Returns the team, local and environmental config file names for [teamFileName], for example
 * `project.settings{,.local,.env}.json`.
 */
internal fun localityConfigNames(teamFileName: String): List<String> =
  LayerLocality.entries.map { localityConfigName(teamFileName, it) }

/**
 * Deletes every locality variant of [teamFileName] under [dir] and refreshes the VFS. The options directories of the
 * application and the project are real, persistent paths, so a stale file from an earlier run can stay there. A
 * delete of only the local variant would leave a stale team or environmental layer behind.
 */
internal fun clearLocalityConfigs(dir: Path, teamFileName: String) {
  for (name in localityConfigNames(teamFileName)) {
    val path = dir.resolve(name)
    Files.deleteIfExists(path)
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
  }
}

/**
 * Returns the text of the [locality] config of [dir], or `null` when the file is absent. The default locality gives
 * `.jetbrains.local.json`, where a plain write lands.
 */
internal fun readConfigOrNull(dir: VirtualFile, locality: LayerLocality = LayerLocality.LOCAL): String? =
  dir.toNioPath().resolve(localityConfigName(FileLayerService.CONFIG_FILE, locality)).takeIf { Files.exists(it) }?.let { Files.readString(it) }

/** Returns the text of the local config (`.jetbrains.local.json`) of [dir]. Fails when the file is absent. */
internal fun readConfig(dir: VirtualFile): String =
  Files.readString(dir.toNioPath().resolve(localConfigName(FileLayerService.CONFIG_FILE)))

/** Waits until the [locality] config of [dir] on disk holds [value], for up to [DISK_TIMEOUT]. */
internal suspend fun awaitConfigContains(dir: VirtualFile, locality: LayerLocality, value: String) {
  withTimeout(DISK_TIMEOUT) {
    while (readConfigOrNull(dir, locality)?.contains("\"$value\"") != true) delay(10.milliseconds)
  }
}

/** Waits until the [locality] config of [dir] on disk does not hold [value], for up to [DISK_TIMEOUT]. */
internal suspend fun awaitConfigLacks(dir: VirtualFile, locality: LayerLocality, value: String) {
  withTimeout(DISK_TIMEOUT) {
    while (readConfigOrNull(dir, locality)?.contains("\"$value\"") == true) delay(10.milliseconds)
  }
}

/**
 * Waits until the local config of [dir] on disk matches the committed snapshot at [snapshotResourcePath], for up to
 * [DISK_TIMEOUT]. Then compares them, so a mismatch reports a diff.
 */
internal suspend fun assertConfigMatchesSnapshot(dir: VirtualFile, snapshotResourcePath: String) {
  val expectedPath = testResourcePath(snapshotResourcePath)
  val expected = Files.readString(Path.of(expectedPath)).trim()
  withTimeout(DISK_TIMEOUT) {
    while (readConfigOrNull(dir)?.trim() != expected) delay(10.milliseconds)
  }
  UsefulTestCase.assertSameLinesWithFile(expectedPath, readConfig(dir))
}

/**
 * Runs [block] while the VFS watcher of the file layer of [project] is off. So only a vault reload loads a directory
 * config, and only the reload sweep removes the layer of a deleted config. Turns the watcher on again after [block].
 *
 * @return the result of [block]
 */
internal inline fun <T> withFileWatcherDisabled(project: Project, block: () -> T): T {
  val fileService = FileLayerService.getInstance(project)
  fileService.setWatcherEnabled(false)
  try {
    return block()
  }
  finally {
    fileService.setWatcherEnabled(true)
  }
}

/**
 * Runs [block] while it may log errors, so a logged error does not fail the test.
 *
 * @return the result of [block] and the messages of the errors it logged
 */
internal inline fun <T> collectingErrors(block: () -> T): Pair<T, List<String>> {
  val errors = CopyOnWriteArrayList<String>()
  val collector = object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
      errors.add(message)
      return Action.NONE
    }
  }
  val result = LoggedErrorProcessor.executeWith(collector).use { block() }
  return result to errors
}

/**
 * Runs [block] and collects the warnings it logs.
 *
 * @return the result of [block] and the messages of the warnings it logged
 */
internal inline fun <T> collectingWarnings(block: () -> T): Pair<T, List<String>> {
  val warnings = CopyOnWriteArrayList<String>()
  val collector = object : LoggedErrorProcessor() {
    override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
      warnings.add(message)
      return false
    }
  }
  val result = LoggedErrorProcessor.executeWith(collector).use { block() }
  return result to warnings
}
