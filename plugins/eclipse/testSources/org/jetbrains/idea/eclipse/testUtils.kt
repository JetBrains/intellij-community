// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.loadProject
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.PathUtil
import com.intellij.util.io.*
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader
import java.nio.file.Path

internal val eclipseTestDataRoot: Path
  get() = PluginPathManager.getPluginHome("eclipse").toPath().resolve("testData")

internal fun checkLoadSaveRoundTrip(testDataDirs: List<Path>,
                                    tempDirectory: TempDirectory,
                                    setupPathVariables: Boolean = false,
                                    imlFilePaths: List<Pair<String, String>>) {
  loadEditSaveAndCheck(testDataDirs, tempDirectory, setupPathVariables, imlFilePaths, ::forceSave, {})
}

internal fun checkEmlFileGeneration(testDataDirs: List<Path>,
                                    tempDirectory: TempDirectory,
                                    imlFilePaths: List<Pair<String, String>>,
                                    edit: (Project) -> Unit = {},
                                    updateExpectedDir: (Path) -> Unit = {}) {
  val emlFileName = "${imlFilePaths.first().second.substringAfterLast('/')}.eml"
  loadEditSaveAndCheck(testDataDirs, tempDirectory, false, imlFilePaths, edit, updateExpectedDir, listOf(emlFileName))
}

internal fun checkConvertToStandardStorage(testDataDirs: List<Path>,
                                           tempDirectory: TempDirectory,
                                           expectedIml: Path,
                                           setupPathVariables: Boolean,
                                           imlFilePaths: List<Pair<String, String>>) {
  fun edit(project: Project) {
    val moduleName = imlFilePaths.first().second.substringAfterLast('/')
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: error("Cannot find module '$moduleName'")
    ClasspathStorage.setStorageType(ModuleRootManager.getInstance(module), ClassPathStorageUtil.DEFAULT_STORAGE)
  }

  fun updateExpectedDir(projectDir: Path) {
    expectedIml.copy(projectDir.resolve("${imlFilePaths.first().second}.iml"))
  }

  loadEditSaveAndCheck(testDataDirs, tempDirectory, setupPathVariables, imlFilePaths, ::edit, ::updateExpectedDir)
}


internal fun loadEditSaveAndCheck(testDataDirs: List<Path>,
                                  tempDirectory: TempDirectory,
                                  setupPathVariables: Boolean = false,
                                  imlFilePaths: List<Pair<String, String>>,
                                  edit: (Project) -> Unit,
                                  updateExpectedDir: (Path) -> Unit,
                                  fileSuffixesToCheck: List<String> = listOf("/.classpath", ".iml")) {
  val originalProjectDir = tempDirectory.newDirectory("original").toPath()
  testDataDirs.forEach {
    FileUtil.copyDir(it.toFile(), originalProjectDir.toFile())
  }
  val projectDir = tempDirectory.newDirectory("project").toPath()
  originalProjectDir.toFile().walk().filter { it.name == ".classpath" }.forEach {
    val text = it.readText().replace("\$ROOT\$", projectDir.systemIndependentPath)
    it.writeText(if (SystemInfo.isWindows) text else text.replace("${EclipseXml.FILE_PROTOCOL}/", EclipseXml.FILE_PROTOCOL))
  }
  val modulesXml = originalProjectDir.resolve(".idea/modules.xml")
  modulesXml.write(imlFilePaths.fold(modulesXml.readText()) { text, (original, replacement) ->
    text.replace("/$original.iml", "/$replacement.iml")
  })
  for ((originalPath, targetPath) in imlFilePaths) {
    originalProjectDir.resolve("$originalPath.iml").move(originalProjectDir.resolve("$targetPath.iml"))
  }

  FileUtil.copyDir(originalProjectDir.toFile(), projectDir.toFile())
  VfsUtil.markDirtyAndRefresh(false, true, true, projectDir.toFile())

  val pathVariables = if (setupPathVariables) mapOf("variable" to "variableidea", "srcvariable" to "srcvariableidea") else emptyMap()
  for ((name, relativePath) in pathVariables) {
    PathMacros.getInstance().setMacro(name, projectDir.resolve(relativePath).toAbsolutePath().toString())
  }
  val junitUrls = mapOf(
    "JUNIT4_PATH" to EclipseClasspathReader.getJunitClsUrl(true)
  )
  for ((name, url) in junitUrls) {
    PathMacros.getInstance().setMacro(name, FileUtil.toSystemIndependentName(PathUtil.getLocalPath(VfsUtil.urlToPath(url))))
  }

  updateExpectedDir(originalProjectDir)
  try {
    runBlocking {
      loadProject(projectDir) { project ->
        runWriteActionAndWait {
          edit(project)
        }
        project.stateStore.save(true)
        projectDir.assertMatches(directoryContentOf(originalProjectDir), filePathFilter = { path ->
          fileSuffixesToCheck.any { path.endsWith(it) }
        }, fileTextMatcher = FileTextMatcher.ignoreXmlFormatting())
      }
    }
  }
  finally {
    pathVariables.keys.forEach {
      PathMacros.getInstance().setMacro(it, null)
    }
    junitUrls.keys.forEach {
      PathMacros.getInstance().setMacro(it, null)
    }
  }
}

internal fun forceSave(project: Project) {
  ModuleManager.getInstance(project).modules.forEach {
    it.moduleFile!!.delete(project)
    it.stateStore.clearCaches()
  }
  JpsProjectModelSynchronizer.getInstance(project)!!.markAllEntitiesAsDirty()
}