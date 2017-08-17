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
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert
import java.io.File

/**
 * Provides access to IntelliJ project configuration so the tests from IntelliJ project sources may locate the project libraries without
 * hardcoding paths to their JARs.
 *
 * @author nik
 */
class IntelliJProjectConfiguration {
  private val projectHome = PathManager.getHomePath()
  private val projectLibraryClassesRoots: Map<String, List<File>>
  private val projectLibrarySourcesUrls: Map<String, List<String>>

  init {
    val m2Repo = FileUtil.toSystemIndependentName(File(SystemProperties.getUserHome(), ".m2/repository").absolutePath)
    val project = JpsSerializationManager.getInstance().loadProject(projectHome, mapOf("MAVEN_REPOSITORY" to m2Repo))
    projectLibraryClassesRoots = project.libraryCollection.libraries.associateBy({ it.name }, { it.getFiles(JpsOrderRootType.COMPILED) })
    projectLibrarySourcesUrls = project.libraryCollection.libraries.associateBy({ it.name }, { it.getRootUrls(JpsOrderRootType.SOURCES) })
  }

  private fun getProjectLibraryClassesRoots(libraryName: String): List<File> {
    return instance.projectLibraryClassesRoots[libraryName]
           ?: throw IllegalArgumentException("Cannot find project library '$libraryName' in ${instance.projectHome}")
  }

  private fun getProjectLibrarySourceRoots(libraryName: String): List<String> {
    return instance.projectLibrarySourcesUrls[libraryName]
           ?: throw IllegalArgumentException("Cannot find project library '$libraryName' in ${instance.projectHome}")
  }

  companion object {
    private val instance by lazy { IntelliJProjectConfiguration() }

    @JvmStatic
    fun getProjectLibraryClassesRootPaths(libraryName: String): List<String> {
      return instance.getProjectLibraryClassesRoots(libraryName).map { FileUtil.toSystemIndependentName(it.absolutePath) }
    }

    @JvmStatic
    fun getProjectLibraryClassesRootUrls(libraryName: String): List<String> {
      return instance.getProjectLibraryClassesRoots(libraryName).map { JpsPathUtil.getLibraryRootUrl(it) }
    }

    @JvmStatic
    fun getProjectLibrarySourceRootUrls(libraryName: String): List<String> {
      return instance.getProjectLibrarySourceRoots(libraryName)
    }

    @JvmStatic
    fun getJarFromSingleJarProjectLibrary(projectLibraryName: String): VirtualFile {
      val jarUrl = UsefulTestCase.assertOneElement(getProjectLibraryClassesRootUrls(projectLibraryName))
      val jarRoot = VirtualFileManager.getInstance().refreshAndFindFileByUrl(jarUrl)
      Assert.assertNotNull(jarRoot)
      return jarRoot!!
    }

  }
}