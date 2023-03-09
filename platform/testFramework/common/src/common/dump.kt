// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.util.MemoryDumpHelper
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

internal const val HEAP_DUMP_IS_PUBLISHED = "Heap dump is published to "

@TestOnly
fun publishHeapDump(fileNamePrefix: String): String {
  val uuid = UUID.randomUUID().toString().substring(1..4)
  val fileName = "$fileNamePrefix-$uuid.hprof.zip"
  val dumpFile = Paths.get(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir")), fileName)
  try {
    Files.deleteIfExists(dumpFile)
    MemoryDumpHelper.captureMemoryDumpZipped(dumpFile)
  }
  catch (e: Exception) {
    e.printStackTrace()
  }

  val dumpPath = dumpFile.toAbsolutePath().toString()
  println("##teamcity[publishArtifacts '$dumpPath']")
  //##teamcity lines are not rendered by IJ console, so let's duplicate the text for IJ users
  println("$HEAP_DUMP_IS_PUBLISHED$dumpPath")
  return dumpPath
}
