// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Dumps memory snapshot for workspace model of IDEA Ultimate project to user home directory
 */
fun main() {
  TestApplicationManager.getInstance()
  val data = loadProject()
  val dumpFile = File(SystemProperties.getUserHome(), "workspace-model.hprof")
  FileUtil.delete(dumpFile)
  MemoryDumpHelper.captureMemoryDump(dumpFile.absolutePath)
  println(data)
  exitProcess(0)
}

private fun loadProject(): Pair<JpsProjectSerializers, TypedEntityStorage> {
  val builder = TypedEntityStorageBuilder.create()
  val virtualFileManager = VirtualFileUrlManagerImpl()
  val projectDir = File(PathManager.getHomePath()).asConfigLocation(virtualFileManager)
  val serializers = JpsProjectEntitiesLoader.loadProject(projectDir, builder, Paths.get("/tmp"), virtualFileManager)
  return Pair(serializers, builder.toStorage())
}