// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.loadProject
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths

internal fun checkLoadSaveRoundTrip(testDataDirs: List<Path>,
                                    tempDirectory: TempDirectory,
                                    setupPathVariables: Boolean = false) {
  val projectDir = tempDirectory.rootPath
  testDataDirs.forEach {
    FileUtil.copyDir(it.toFile(), projectDir.toFile())
  }
  VfsUtil.markDirtyAndRefresh(false, true, true, tempDirectory.root)

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
        val baseDir = Paths.get(project.basePath!!)
        val expected = testDataDirs.drop(1).fold(directoryContentOf(testDataDirs.first())) { content, dir ->
          content.mergeWith(directoryContentOf(dir))
        }
        baseDir.assertMatches(expected, filePathFilter = { path ->
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

