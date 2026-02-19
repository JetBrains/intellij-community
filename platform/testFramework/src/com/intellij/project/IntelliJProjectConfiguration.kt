// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PathUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsMavenSettings.getMavenRepositoryPath
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

/**
 * Provides access to IntelliJ project configuration so the tests from IntelliJ project sources may locate project and module libraries
 * without hard-coding paths to their JARs.
 */
class IntelliJProjectConfiguration {
  private val projectHome = PathManager.getHomeDir().toString()
  private val projectLibraries: Map<String, LibraryRoots>
  private val moduleLibraries: Map<String, Map<String, LibraryRoots>>
  private val remoteRepositoryDescriptions: List<JpsRemoteRepositoryDescription>

  init {
    val project = loadIntelliJProject(projectHome)
    fun extractLibrariesRoots(collection: JpsLibraryCollection): Map<@NlsSafe String, LibraryRoots> =
      collection.libraries.associateBy(keySelector = { it.name }, valueTransform = {
        LibraryRoots(
          classes = java.util.List.copyOf(it.getFiles(JpsOrderRootType.COMPILED)),
          sources = java.util.List.copyOf(it.getFiles(JpsOrderRootType.SOURCES)),
        )
      })
    projectLibraries = extractLibrariesRoots(project.libraryCollection)
    moduleLibraries = project.modules.associateBy(keySelector = {it.name}, valueTransform = {
      extractLibrariesRoots(it.libraryCollection).ifEmpty { emptyMap() }
    })
    remoteRepositoryDescriptions = JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(project)!!.repositories
  }

  companion object {
    private val instance by lazy { IntelliJProjectConfiguration() }

    @JvmStatic
    fun getRemoteRepositoryDescriptions() : List<JpsRemoteRepositoryDescription> = instance.remoteRepositoryDescriptions

    @JvmStatic
    fun getProjectLibraryClassesRootPaths(libraryName: String): List<String> = getProjectLibrary(libraryName).classesPaths

    @JvmStatic
    fun getProjectLibraryClassesRootUrls(libraryName: String): List<String> = getProjectLibrary(libraryName).classesUrls

    @JvmStatic
    fun getProjectLibrary(libraryName: String): LibraryRoots =
      instance.projectLibraries[libraryName]
      ?: throw IllegalArgumentException("Cannot find project library '$libraryName' in ${instance.projectHome}")

    @JvmStatic
    fun getModuleLibrary(moduleName: String, libraryName: String): LibraryRoots =
      (instance.moduleLibraries[moduleName] ?: throw IllegalArgumentException("Cannot find module '$moduleName' in ${instance.projectHome}")).let {
        it[libraryName] ?: throw IllegalArgumentException("Cannot find module library '$libraryName' in $moduleName")
      }

    @JvmStatic
    fun getJarFromSingleJarProjectLibrary(projectLibraryName: String): VirtualFile = getVirtualFile(getProjectLibrary(projectLibraryName))

    @JvmStatic
    fun getVirtualFile(lib: LibraryRoots): VirtualFile =
      lib.classesUrls.single().let { url ->
        VirtualFileManager.getInstance().refreshAndFindFileByUrl(url)
        ?: throw IllegalStateException("Cannot find virtual file by $url (nio file exists: ${lib.classes.single().exists()})")
      }

    @JvmStatic
    fun getJarPathFromSingleJarProjectLibrary(libName: String): Path = getProjectLibrary(libName).classes.single().toPath()

    @JvmStatic
    fun loadIntelliJProject(projectHome: String): JpsProject {
      val m2Repo = getLocalMavenRepo().invariantSeparatorsPathString
      val project = JpsSerializationManager.getInstance().loadProject(projectHome, mapOf("MAVEN_REPOSITORY" to m2Repo), true)
      val pathUtilJarPath = Path.of(PathUtil.getJarPathForClass(PathUtil::class.java))
      val outPath: Path?
      val jpsJavaProjectExtension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project)
      if (pathUtilJarPath.isDirectory()) {
        outPath = pathUtilJarPath.parent.parent
        jpsJavaProjectExtension.outputUrl = outPath.toString()
      }
      else {
        println("Running from jars, would not change JpsJavaExtensionService.outputUrl, current is '${jpsJavaProjectExtension.outputUrl}'")
      }
      return project
    }

    @JvmStatic
    fun getLocalMavenRepo(): Path = Path.of(getMavenRepositoryPath())
  }

  class LibraryRoots(val classes: List<java.io.File>, val sources: List<java.io.File>) {
    val classesPaths: List<String>
      get() = classes.map { FileUtil.toSystemIndependentName(it.absolutePath) }

    val classesUrls: List<String>
      get() = classes.map { JpsPathUtil.getLibraryRootUrl(it) }

    val sourcesUrls: List<String>
      get() = sources.map { JpsPathUtil.getLibraryRootUrl(it) }
  }
}
