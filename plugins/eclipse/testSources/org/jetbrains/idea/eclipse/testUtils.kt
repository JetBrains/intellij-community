// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.loadProject
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.*
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

internal fun checkLoadSaveRoundTrip(testDataDirs: List<Path>,
                                    tempDirectory: TempDirectory,
                                    setupPathVariables: Boolean = false,
                                    imlFilePaths: List<Pair<String, String>>) {
  val originalProjectDir = tempDirectory.newDirectory("original").toPath()
  testDataDirs.forEach {
    FileUtil.copyDir(it.toFile(), originalProjectDir.toFile())
  }
  val projectDir = tempDirectory.newDirectory("project").toPath()
  originalProjectDir.toFile().walk().filter { it.name == ".classpath" }.forEach {
    val text = it.readText().replace("\$ROOT\$", projectDir.systemIndependentPath)
    it.writeText(if (SystemInfo.isWindows) text else text.replace("${EclipseXml.FILE_PROTOCOL}/", EclipseXml.FILE_PROTOCOL))
  }
  val modulesXml = originalProjectDir / ".idea/modules.xml"
  modulesXml.write(imlFilePaths.fold(modulesXml.readText()) { text, (original, replacement) ->
    text.replace("/$original.iml", "/$replacement.iml")
  })
  for ((originalPath, targetPath) in imlFilePaths) {
    (originalProjectDir / "$originalPath.iml").move(originalProjectDir / "$targetPath.iml")
  }

  FileUtil.copyDir(originalProjectDir.toFile(), projectDir.toFile())
  VfsUtil.markDirtyAndRefresh(false, true, true, projectDir.toFile())

  val pathVariables = if (setupPathVariables) mapOf("variable" to "variableidea", "srcvariable" to "srcvariableidea") else emptyMap()
  for ((name, relativePath) in pathVariables) {
    PathMacros.getInstance().setMacro(name, projectDir.resolve(relativePath).toAbsolutePath().toString())
  }

  try {
    runBlocking {
      loadProject(projectDir) { project ->
        runWriteActionAndWait {
          ModuleManager.getInstance(project).modules.forEach {
            it.moduleFile!!.delete(this)
            it.stateStore.clearCaches()
          }
          if (WorkspaceModel.isEnabled) {
            JpsProjectModelSynchronizer.getInstance(project)!!.markAllEntitiesAsDirty()
          }
        }
        project.stateStore.save(true)
        projectDir.assertMatches(directoryContentOf(originalProjectDir), filePathFilter = { path ->
          path.endsWith("/.classpath") || path.endsWith(".iml")
        })
      }
    }
  }
  finally {
    pathVariables.keys.forEach {
      PathMacros.getInstance().setMacro(it, null)
    }
  }
}

