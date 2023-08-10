// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializers
import com.intellij.testFramework.TestApplicationManager
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.workspaceModel.ide.impl.jps.serialization.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Dumps memory snapshot for workspace model of IDEA Ultimate project to user home directory
 */
fun main() {
  TestApplicationManager.getInstance()
  val data = runBlocking { loadProject() }
  val dumpFile = File(SystemProperties.getUserHome(), "workspace-model.hprof")
  FileUtil.delete(dumpFile)
  MemoryDumpHelper.captureMemoryDump(dumpFile.absolutePath)
  println(data)
  exitProcess(0)
}

private suspend fun loadProject(): Pair<JpsProjectSerializers, EntityStorage> {
  val builder = MutableEntityStorage.create()
  val virtualFileManager = VirtualFileUrlManagerImpl()
  val projectDir = File(PathManager.getHomePath()).asConfigLocation(virtualFileManager)
  val context = SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(projectDir))
  val serializers = JpsProjectEntitiesLoader.loadProject(projectDir, builder, builder, Paths.get("/tmp"), TestErrorReporter,
                                                         context = context)
  return Pair(serializers, builder.toSnapshot())
}