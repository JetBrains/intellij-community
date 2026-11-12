// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApiHelpers
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.PathUtil
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.serialization.JpsMavenSettings.getMavenRepositoryPath
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
      val helperModuleName = getJpsModuleNameForClass(clazz)
      logger.value.info("helper module name: $helperModuleName")

      val helperModule = module(helperModuleName)

      val dependencies = JpsJavaExtensionService
        .dependencies(helperModule)
        .recursively()

      val libraries = dependencies
        .libraries
        .flatMap { getLibraryJars(it) }

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

    /**
     * Returns the compiled jars of [library].
     *
     * Bazel does not fill the Maven repository, so the jars come from the Bazel runfiles under Bazel.
     * The Maven repository holds the jars for a JPS run.
     */
    private fun getLibraryJars(library: JpsLibrary): List<Path> {
      if (!BazelRunfiles.isRunningFromBazel) {
        val mavenPath = getMavenRepositoryPath()
        return library.getPaths(JpsOrderRootType.COMPILED)
          .map { Path(it.pathString.replace('$' + PathMacrosImpl.MAVEN_REPOSITORY + '$', mavenPath)) }
      }
      val moduleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
      val libraryInfo = ArchivedCompilationContextUtil.findLibraryInfo(library.name, moduleName)
                        ?: error("Library ${library.name} is not in the Bazel output")
      return libraryInfo.jarTargets.map { BazelRunfiles.getFileByLabel(BazelLabel.fromString(it)) }
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
      val relevantJarsRoot = ArchivedCompilationContextUtil.archivedCompiledClassesLocation

      if (Files.isDirectory(path)) {
        // plain compilation output
        return path.name
      }
      else if (relevantJarsRoot != null && jarPathForClass.startsWith(relevantJarsRoot)) {
        // archived compilation output
        val mapping = ArchivedCompilationContextUtil.archivedCompiledClassesMapping
        checkNotNull(mapping) { "Mapping cannot be null at this point" }
        // Under Bazel the class can be loaded from a runfiles symlink while the mapping stores the canonical bazel-out path.
        // ./bazel.cmd test --cache_test_results=no //platform/platform-tests:tests_test --test_filter=com.intellij.execution.eel.EelLocalTunnelApiTest
        val jarRealPathForClass = path.toRealPath().pathString
        val key = mapping.entries.firstOrNull { (_, value) ->
          value == jarPathForClass || value == jarRealPathForClass
        }?.key
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
      val relevantJarsRoot = ArchivedCompilationContextUtil.archivedCompiledClassesLocation

      if (Files.isDirectory(path)) {
        // plain compilation output
        return moduleNames.map { path.parent.resolve(it) }
      }
      else if (relevantJarsRoot != null && jarPathForClass.startsWith(relevantJarsRoot)) {
        // archived compilation output, assume we need 'production' output
        val mapping = ArchivedCompilationContextUtil.archivedCompiledClassesMapping
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
