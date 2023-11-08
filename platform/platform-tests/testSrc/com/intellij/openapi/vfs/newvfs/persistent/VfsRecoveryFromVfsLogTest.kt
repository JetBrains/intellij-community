// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogImpl
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.system.exitProcess

@OptIn(ExperimentalPathApi::class)
object VfsRecoveryFromVfsLogTest {
  fun fullRecoveryIsIdentity(cachesDir: Path, dirForRecoveredCaches: Path = cachesDir.resolveSibling("recovered-caches")) {
    println("testing recovery")
    val vfsLog = VfsLogImpl.open(PersistentFSPaths(cachesDir).vfsLogStorage, true)
    vfsLog.query().use {
      println("VfsLog state: begin=${it.begin().getPosition()}, end=${it.end().getPosition()}, " +
              "compacted position=${it.getBaseSnapshot({ throw AssertionError() }, { throw AssertionError() })
                ?.point?.invoke()?.getPosition()}")
    }
    if (dirForRecoveredCaches.exists()) {
      println("$dirForRecoveredCaches exists and will be cleared")
      dirForRecoveredCaches.deleteRecursively()
    }
    val recoveryResult = vfsLog.query().use {
      val endPoint = it.end()
      VfsRecoveryUtils.recoverFromPoint(endPoint, it, cachesDir, dirForRecoveredCaches, invokeReadAction = { it.compute() })
    }
    println(recoveryResult)
    check(recoveryResult.fileStateCounts.getOrElse(VfsRecoveryUtils.RecoveryState.BOTCHED) { 0 } == 0)
    check(recoveryResult.botchedAttributesCount == 0.toLong())

    val baseVfs = FSRecordsImpl.connect(cachesDir, emptyList(), false, FSRecordsImpl.ON_ERROR_RETHROW)
    val recoveredVfs = FSRecordsImpl.connect(dirForRecoveredCaches, emptyList(), false, FSRecordsImpl.ON_ERROR_RETHROW)

    AutoCloseable {
      baseVfs.close()
      recoveredVfs.close()
    }.use {
      val diff = VfsDiffBuilder.buildDiff(baseVfs, recoveredVfs)
      println(diff)
      check(diff.elements.isEmpty())
      check(diff.filesVisited > 10)
    }
    vfsLog.dispose()
  }

  fun doubleRecoveryIsIdentity(cachesDir: Path) {
    val recovered1 = cachesDir.resolveSibling("recovered-caches-1")
    val recovered2 = cachesDir.resolveSibling("recovered-caches-2")
    println("running first recovery $cachesDir -> $recovered1")
    fullRecoveryIsIdentity(cachesDir, recovered1)
    println("OK")
    println("running second recovery $recovered1 -> $recovered2")
    fullRecoveryIsIdentity(recovered1, recovered2)
    println("OK")
  }

  fun VfsLogImpl.forceCompactionUpTo(targetPosition: Long) {
    acquireCompactionContext().use { ctx ->
      val compactionController = getContextImpl().compactionController
      val positionToCompactTo = compactionController.findPositionForCompaction(ctx, targetPosition)
      println("forcing compaction to $positionToCompactTo (target=$targetPosition)")
      compactionController.forceCompactionUpTo(ctx, positionToCompactTo)
    }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size in 1..2) { "Arguments: <path to caches folder> [<position to force compaction to>]" }
    val cachesDir = Path.of(args[0])
    val cachesCopy = cachesDir.resolveSibling("caches-tmp-test-dir")
    println("making a caches copy $cachesDir -> $cachesCopy")
    if (cachesCopy.exists()) cachesCopy.deleteRecursively()
    cachesDir.copyToRecursively(cachesCopy, followLinks = true, overwrite = true)

    if (args.size == 1) {
      doubleRecoveryIsIdentity(cachesCopy)
    }
    else {
      check(args.size == 2)
      val targetPosition = args[1].toLong()

      val vfsLog = VfsLogImpl.open(cachesCopy / "vfslog", true)
      for (i in 4 downTo 1) {
        vfsLog.forceCompactionUpTo(targetPosition / i)
      }
      vfsLog.dispose()

      fullRecoveryIsIdentity(cachesCopy)
    }
    exitProcess(0) // something is hanging, but it is of no importance, TODO fix
  }
}
