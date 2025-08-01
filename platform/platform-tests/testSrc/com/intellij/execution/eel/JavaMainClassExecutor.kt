// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApiHelpers
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString


/**
 * Searches for module with [clazz] in [PathManager.ourHomePath] an executes [clazz] `main` with all dependencies
 */
internal class JavaMainClassExecutor(clazz: Class<*>, vararg args: String) {
  private val exe = Path(ProcessHandle.current().info().command().get()).toString()
  private val env = mapOf("CLASSPATH" to getClassPathForClass(clazz))
  private val args = listOf(clazz.canonicalName) + args.toList()

  /**
   * Execute `main` method
   */
  fun createBuilderToExecuteMain(exec: EelExecApi): EelExecApiHelpers.SpawnProcess = exec.spawnProcess(exe).env(env).args(args)

  private companion object {
    private fun getClassPathForClass(clazz: Class<*>): String {
      val mavenPath = Path(SystemProperties.getUserHome()).resolve(".m2").resolve("repository").toString()
      val helperModuleName = getJpsModuleNameForClass(clazz)
      logger.value.info("helper module name: $helperModuleName")

      val helperModule = module(helperModuleName)

      val dependencies = JpsJavaExtensionService
        .dependencies(helperModule)
        .recursively()

      val libraries = dependencies
        .libraries
        .flatMap { it.getPaths(JpsOrderRootType.COMPILED) }
        .map { Path(it.pathString.replace('$' + PathMacrosImpl.MAVEN_REPOSITORY + '$', mavenPath)) }

      val modules: List<Path> = getJpsModulesOutput(clazz, dependencies.modules.map { it.name })

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
        val helperModule = jpsProject.findModuleByName(helperModuleName)
        if (helperModule != null) return helperModule
        logger.value.warn("$helperModuleName not found in $homePath modules. Checked: ${jpsProject.modules}")
      }
      throw AssertionError("Couldn't find module $helperModuleName")
    }

    private fun getJpsModuleNameForClass(clazz: Class<*>): String {
      val jarPathForClass = PathUtil.getJarPathForClass(clazz)
      val path = Path.of(jarPathForClass)
      val relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation()

      if (Files.isDirectory(path)) {
        // plain compilation output
        return path.name
      }
      else if (relevantJarsRoot != null && jarPathForClass.startsWith(relevantJarsRoot)) {
        // archived compilation output
        val mapping = PathManager.getArchivedCompiledClassesMapping()
        checkNotNull(mapping) { "Mapping cannot be null at this point" }
        val key = mapping.entries.firstOrNull { (_, value) -> value == jarPathForClass }?.key
        if (key == null) {
          throw IllegalStateException("Cannot find path '$jarPathForClass' in mapping values:'$mapping'")
        }
        return key.split('/', limit = 2).last()
      }
      else {
        // production jar
        throw IllegalStateException("Cannot deduce module name from '$path'")
      }
    }

    private fun getJpsModulesOutput(clazz: Class<*>, moduleNames: List<@NlsSafe String>): List<Path> {
      val jarPathForClass = PathUtil.getJarPathForClass(clazz)
      val path = Path.of(jarPathForClass)
      val relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation()

      if (Files.isDirectory(path)) {
        // plain compilation output
        return moduleNames.map { path.parent.resolve(it) }
      }
      else if (relevantJarsRoot != null && jarPathForClass.startsWith(relevantJarsRoot)) {
        // archived compilation output, assume we need 'production' output
        val mapping = PathManager.getArchivedCompiledClassesMapping()
        checkNotNull(mapping) { "Mapping cannot be null at this point" }
        return moduleNames.mapNotNull {
          val key = "production/$it"
          val value = mapping[key]
          if (value == null) logger.value.warn("Not found jar mapping for '$key'")
          value?.let { Path(value) }
        }
      }
      else {
        // production jar
        throw IllegalStateException("Unexpected path '$path'")
      }
    }

    private val logger = lazy { fileLogger() }
  }
}
