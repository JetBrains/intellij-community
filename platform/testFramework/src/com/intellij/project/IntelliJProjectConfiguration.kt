/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.project

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert
import java.io.File

/**
 * Provides access to IntelliJ project configuration so the tests from IntelliJ project sources may locate project and module libraries without
 * hardcoding paths to their JARs.
 *
 * @author nik
 */
class IntelliJProjectConfiguration {
  private val projectHome = PathManager.getHomePath()
  private val projectLibraries: Map<String, LibraryRoots>
  private val moduleLibraries: Map<String, Map<String, LibraryRoots>>

  init {
    val m2Repo = FileUtil.toSystemIndependentName(File(SystemProperties.getUserHome(), ".m2/repository").absolutePath)
    val project = JpsSerializationManager.getInstance().loadProject(projectHome, mapOf("MAVEN_REPOSITORY" to m2Repo))
    fun extractLibrariesRoots(collection: JpsLibraryCollection) = collection.libraries.associateBy({ it.name }, {
      LibraryRoots(SmartList(it.getFiles(JpsOrderRootType.COMPILED)), SmartList(it.getFiles(JpsOrderRootType.SOURCES)))
    })
    projectLibraries = extractLibrariesRoots(project.libraryCollection)
    moduleLibraries = project.modules.associateBy({it.name}, {
      val libraries = extractLibrariesRoots(it.libraryCollection)
      if (libraries.isNotEmpty()) libraries else emptyMap()
    })
  }

  companion object {
    private val instance by lazy { IntelliJProjectConfiguration() }

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
  }

  class LibraryRoots(val classes: List<File>, val sources: List<File>) {
    val classesPaths
      get() = classes.map { FileUtil.toSystemIndependentName(it.absolutePath) }

    val classesUrls
      get() = classes.map { JpsPathUtil.getLibraryRootUrl(it) }

    val sourcesUrls
      get() = sources.map { JpsPathUtil.getLibraryRootUrl(it) }
  }
}
