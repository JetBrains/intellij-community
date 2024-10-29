// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelExecApi
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString


/**
 * Searches for module with [clazz] in [PathManager.ourHomePath] an executes [clazz] `main` with all dependencies
 */
internal class JavaMainClassExecutor(clazz: Class<*>) {
  private val exe = Path(ProcessHandle.current().info().command().get()).toString()
  private val env = mapOf("CLASSPATH" to getClassPathForClass(clazz))
  private val args = listOf(clazz.canonicalName)

  /**
   * Execute `main` method
   */
  fun createBuilderToExecuteMain(): EelExecApi.ExecuteProcessBuilder = EelExecApi.executeProcessBuilder(exe).env(env).args(args)

  private companion object {
    private fun getClassPathForClass(clazz: Class<*>): String {
      val mavenPath = Path(SystemProperties.getUserHome()).resolve(".m2").resolve("repository").toString()
      val helperModuleOutputPath = getJpsModuleOutputPathForClass(clazz)
      logger.value.info("helper module path: $helperModuleOutputPath")
      val helperModuleName = helperModuleOutputPath.name

      val helperModule = module(helperModuleName)


      var dependencies = JpsJavaExtensionService
        .dependencies(helperModule)
        .recursively()

      val libraries = dependencies
        .libraries
        .flatMap { it.getPaths(JpsOrderRootType.COMPILED) }
        .map { Path(it.pathString.replace('$' + PathMacrosImpl.MAVEN_REPOSITORY + '$', mavenPath)) }
      val modulesOutputPath = helperModuleOutputPath.parent

      val modules = dependencies
        .modules
        .map { module -> modulesOutputPath.resolve(module.name) }

      return (modules + libraries)
        .filter { path ->
          path.exists().also {
            if (!it) {
              logger.value.info("$path doesn't exist")
            }
          }
        }
        .joinToString(File.pathSeparator)
    }

    private fun module(helperModuleName: String): JpsModule {
      for (homePath in arrayOf(PathManager.getHomePath(), PathManager.getCommunityHomePath())) {
        val jpsProject = JpsSerializationManager.getInstance().loadProject(homePath, mapOf())
        val helperModule = jpsProject.modules.firstOrNull { module -> module.name == helperModuleName }
        if (helperModule != null) return helperModule
        logger.value.warn("$helperModuleName not found in $homePath modules. Checked: ${jpsProject.modules}")
      }
      throw AssertionError("Couldn't find module $helperModuleName")
    }


    private fun getJpsModuleOutputPathForClass(clazz: Class<*>): Path {
      val classFile = clazz.name.replace(".", "/") + ".class"

      val absoluteClassPath = clazz.classLoader.getResource(classFile)!!.path.let { path ->
        Path(if (SystemInfoRt.isWindows) path.trimStart('/') else path)
      }
      logger.value.info("Looking for class file $absoluteClassPath")

      val relativeClassPath = Path(classFile)
      var pathToModule = absoluteClassPath
      while (pathToModule.toList().size > 1) {
        if (pathToModule.resolve(relativeClassPath).exists()) break
        pathToModule = pathToModule.parent
      }
      return pathToModule
    }

    private val logger = lazy { fileLogger() }
  }
}
