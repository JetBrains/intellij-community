// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.testFramework.monorepo

import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.Path

object MonorepoProjectStructure {
  val communityHomePath: String = PlatformTestUtil.getCommunityPath()
  val communityRoot: Path = Path(communityHomePath)
  val communityProject: JpsProject by lazy { IntelliJProjectConfiguration.loadIntelliJProject(communityHomePath) }

  val JpsModule.baseDirectory: Path
    get() = JpsModelSerializationDataService.getModuleExtension(this)!!.baseDirectoryPath
  
  fun JpsModule.isFromCommunity(): Boolean = baseDirectory.startsWith(communityRoot)
  
  fun JpsLibrary.isFromCommunity(): Boolean = getPaths(JpsOrderRootType.COMPILED).all { it.startsWith(communityRoot) }
}

fun JpsModule.hasProductionSources(): Boolean = getSourceRoots(JavaSourceRootType.SOURCE).iterator().hasNext()

/**
 * Calls [processor] for the path containing the production output of [this@processModuleProductionOutput].
 * Works both when module output is located in a directory and when it's packed in a JAR.
 */
fun <T> JpsModule.processProductionOutput(processor: (outputRoot: Path) -> T): T {
  val archivedCompiledClassesMapping = PathManager.getArchivedCompiledClassesMapping()
  val outputJarPath = archivedCompiledClassesMapping?.get("production/$name")
  if (outputJarPath == null) {
    val outputDirectoryPath = JpsJavaExtensionService.getInstance().getOutputDirectoryPath(this, false)
                              ?: error("Output directory is not specified for '$name'")
    return processor(outputDirectoryPath)
  }
  else {
    return FileSystems.newFileSystem(Path(outputJarPath)).use { 
      processor(it.rootDirectories.single())
    }
  }
}