// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FileBackedSessionInvalidationStateTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun refreshPingDoesNotMarkDirtyPaths() {
    val state = newState()
    val sessionPath = tempDir.resolve("refresh/session.jsonl")
    val stat = stat(sessionPath)
    state.applyParsedUpdates(mapOf(stat.pathKey to cachedFile(stat, parsedValue = "initial")))

    val markedPaths = state.markDirty(FileBackedSessionChangeSet())
    val plan = state.planRescan(mapOf(stat.pathKey to stat))

    assertThat(markedPaths).isZero()
    assertThat(plan.filesToParse).isEmpty()
    assertThat(plan.dirtyPathCount).isZero()
    assertThat(plan.fullRescan).isFalse()
  }

  @Test
  fun dirtyPathForcesReparseWhenFileStatsDidNotChange() {
    val state = newState()
    val sessionPath = tempDir.resolve("dirty/session.jsonl")
    val stat = stat(sessionPath)
    state.applyParsedUpdates(mapOf(stat.pathKey to cachedFile(stat, parsedValue = "before")))

    val markedPaths = state.markDirty(FileBackedSessionChangeSet(changedPaths = setOf(sessionPath)))
    val plan = state.planRescan(mapOf(stat.pathKey to stat))

    assertThat(markedPaths).isEqualTo(1)
    assertThat(plan.filesToParse).containsExactly(stat)
    assertThat(plan.dirtyPathCount).isEqualTo(1)
    assertThat(plan.fullRescan).isFalse()
  }

  @Test
  fun fullRescanReparsesAllScannedFiles() {
    val state = newState()
    val firstStat = stat(tempDir.resolve("full/first.jsonl"), lastModifiedNs = 1L)
    val secondStat = stat(tempDir.resolve("full/second.jsonl"), lastModifiedNs = 2L)
    state.applyParsedUpdates(
      mapOf(
        firstStat.pathKey to cachedFile(firstStat, parsedValue = "first"),
        secondStat.pathKey to cachedFile(secondStat, parsedValue = "second"),
      )
    )

    state.markDirty(FileBackedSessionChangeSet(requiresFullRescan = true))
    val plan = state.planRescan(
      linkedMapOf(
        firstStat.pathKey to firstStat,
        secondStat.pathKey to secondStat,
      )
    )

    assertThat(plan.filesToParse).containsExactly(firstStat, secondStat)
    assertThat(plan.fullRescan).isTrue()
  }

  @Test
  fun removedFilesArePrunedFromCache() {
    val state = newState()
    val keptStat = stat(tempDir.resolve("prune/kept.jsonl"), lastModifiedNs = 1L)
    val removedStat = stat(tempDir.resolve("prune/removed.jsonl"), lastModifiedNs = 2L)
    state.applyParsedUpdates(
      mapOf(
        keptStat.pathKey to cachedFile(keptStat, parsedValue = "kept"),
        removedStat.pathKey to cachedFile(removedStat, parsedValue = "removed"),
      )
    )

    val plan = state.planRescan(mapOf(keptStat.pathKey to keptStat))
    val cachedFiles = state.snapshotCachedFiles()

    assertThat(plan.removedAny).isTrue()
    assertThat(plan.filesToParse).isEmpty()
    assertThat(cachedFiles).containsOnlyKeys(keptStat.pathKey)
  }
}

private fun newState(): FileBackedSessionInvalidationState<String> {
  return FileBackedSessionInvalidationState { path ->
    path.fileName?.toString()?.endsWith(".jsonl") == true
  }
}

private fun stat(
  path: Path,
  lastModifiedNs: Long = 1L,
  sizeBytes: Long = 100L,
): FileBackedSessionFileStat {
  return FileBackedSessionFileStat(
    pathKey = toFileBackedSessionPathKey(path),
    path = normalizeFileBackedSessionPath(path),
    lastModifiedNs = lastModifiedNs,
    sizeBytes = sizeBytes,
  )
}

private fun cachedFile(
  stat: FileBackedSessionFileStat,
  parsedValue: String,
): FileBackedSessionCachedFile<String> {
  return FileBackedSessionCachedFile(
    lastModifiedNs = stat.lastModifiedNs,
    sizeBytes = stat.sizeBytes,
    parsedValue = parsedValue,
  )
}
