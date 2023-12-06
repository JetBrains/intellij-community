// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.findOrCreateFile
import java.nio.file.Path
import kotlin.io.path.writeText


fun dumpThreads(name: String, stripCoroutineDump: Boolean = true): Path {
  val threadInfos = ThreadDumper.getThreadInfos()
  val threadDumpInfo = ThreadDumper.getThreadDumpInfo(threadInfos, stripCoroutineDump)
  val currentTime = System.currentTimeMillis()
  return Path.of(PathManager.getLogPath())
    .findOrCreateFile("$name-$currentTime.thread-dump")
    .apply { writeText(threadDumpInfo.rawDump) }
}