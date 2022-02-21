// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.project

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Provides access to IntelliJ project configuration so the tests from IntelliJ project sources may locate project and module libraries without
 * hardcoding paths to their JARs.
 */
class IntelliJProjectConfiguration {
  private val projectHome = PathManager.getHomePath()
  private val projectLibraries: Map<String, LibraryRoots>
  private val moduleLibraries: Map<String, Map<String, LibraryRoots>>

  private val remoteRepositoryDescriptions : List<JpsRemoteRepositoryDescription>

  init {
    val project = loadIntelliJProject(projectHome)
    fun extractLibrariesRoots(collection: JpsLibraryCollection) = collection.libraries.associateBy({ it.name }, {
      LibraryRoots(SmartList(it.getFiles(JpsOrderRootType.COMPILED)), SmartList(it.getFiles(JpsOrderRootType.SOURCES)))
    })
    projectLibraries = extractLibrariesRoots(project.libraryCollection)
    moduleLibraries = project.modules.associateBy({it.name}, {
      val libraries = extractLibrariesRoots(it.libraryCollection)
      if (libraries.isNotEmpty()) libraries else emptyMap()
    })

    remoteRepositoryDescriptions = JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(project)!!.repositories
  }

  companion object {
    private val instance by lazy { IntelliJProjectConfiguration() }

    @JvmStatic
    fun getRemoteRepositoryDescriptions() : List<JpsRemoteRepositoryDescription> {
      return instance.remoteRepositoryDescriptions
    }

    @JvmStatic
    fun getProjectLibraryClassesRootPaths(libraryName: String): List<String> {
      return getProjectLibrary(libraryName).classesPaths
    }

    @JvmStatic
    fun getProjectLibraryClassesRootUrls(libraryName: String): List<String> {
      return getProjectLibrary(libraryName).classesUrls
    }

    @JvmStatic
    fun getProjectLibrary(libraryName: String): LibraryRoots {
      return instance.projectLibraries[libraryName]
             ?: throw IllegalArgumentException("Cannot find project library '$libraryName' in ${instance.projectHome}")
    }

    @JvmStatic
    fun getModuleLibrary(moduleName: String, libraryName: String): LibraryRoots {
      val moduleLibraries = instance.moduleLibraries[moduleName]
                            ?: throw IllegalArgumentException("Cannot find module '$moduleName' in ${instance.projectHome}")
      return moduleLibraries[libraryName]
             ?: throw IllegalArgumentException("Cannot find module library '$libraryName' in $moduleName")
    }

    @JvmStatic
    fun getJarFromSingleJarProjectLibrary(projectLibraryName: String): VirtualFile {
      val jarUrl = UsefulTestCase.assertOneElement(getProjectLibraryClassesRootUrls(projectLibraryName))
      val jarRoot = VirtualFileManager.getInstance().refreshAndFindFileByUrl(jarUrl)
      Assert.assertNotNull(jarRoot)
      return jarRoot!!
    }

    @JvmStatic
    fun loadIntelliJProject(projectHome: String): JpsProject {
      val m2Repo = getLocalMavenRepo().systemIndependentPath
      return JpsSerializationManager.getInstance().loadProject(projectHome, mapOf(PathMacrosImpl.MAVEN_REPOSITORY to m2Repo), true)
    }

    @JvmStatic
    fun getLocalMavenRepo(): Path = Paths.get(SystemProperties.getUserHome(), ".m2/repository")
  }

  class LibraryRoots(val classes: List<File>, val sources: List<File>) {
    val classesPaths: List<String>
      get() = classes.map { FileUtil.toSystemIndependentName(it.absolutePath) }

    val classesUrls: List<String>
      get() = classes.map { JpsPathUtil.getLibraryRootUrl(it) }

    val sourcesUrls: List<String>
      get() = sources.map { JpsPathUtil.getLibraryRootUrl(it) }
  }
}
