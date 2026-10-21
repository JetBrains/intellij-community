// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.ProjectStorePathManager
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG: Logger = Logger.getInstance("FakeModule")

/**
 * This method is used to create a "fake" module during project opening before running DirectoryProjectConfigurators
 * or when one of them needs it
 *
 * @see com.intellij.platform.PlatformProjectOpenProcessor.runDirectoryProjectConfigurators
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
fun Ref<Module>.getOrInitializeModule(project: Project, baseDir: VirtualFile): Module {
  get()?.let {
    return it
  }

  val module = runBlockingCancellable {
    createFakeModuleForDirectoryProjectConfigurators(
      projectVirtualFile = baseDir,
      moduleManager = ModuleManager.getInstance(project),
      projectFile = null,
    )
  }
  set(module)
  return module
}

internal suspend fun createFakeModuleForDirectoryProjectConfigurators(
  projectVirtualFile: VirtualFile,
  moduleManager: ModuleManager,
  projectFile: Path?,
): Module {
  existingModule(moduleManager)?.let {
    return it
  }
  val imlPath = fakeModuleImlPath(projectVirtualFile, projectFile)
  return backgroundWriteAction {
    createFakeModule(projectVirtualFile, moduleManager, imlPath)
  }
}

private fun existingModule(moduleManager: ModuleManager): Module? {
  val modules = moduleManager.modules
  if (modules.isEmpty()) {
    return null
  }
  LOG.info("Fake module cannot be created because modules are already configured (module count: ${modules.size})")
  return modules[0]
}

private fun fakeModuleImlPath(projectVirtualFile: VirtualFile, projectFile: Path?): String {
  // correct module name when opening root of drive as project (RUBY-5181)
  val moduleName = projectVirtualFile.getName().replace(":", "")
  if (projectFile != null) {
    ProjectStorePathManager.getInstance().getStoreDescriptor(projectFile).dotIdea?.let {
      return it.invariantSeparatorsPathString + '/' + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION
    }
  }
  return projectVirtualFile.getPath() + "/.idea/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION
}

@RequiresWriteLock
private fun createFakeModule(projectVirtualFile: VirtualFile, moduleManager: ModuleManager, imlPath: String): Module {
  val module = moduleManager.newModule(imlPath, ModuleTypeManager.getInstance()?.getDefaultModuleType()?.id ?: "unknown")
  val model = ModuleRootManager.getInstance(module).getModifiableModel()
  try {
    val contentRoots = model.getContentRoots()
    if (contentRoots.size == 0) {
      model.addContentEntry(projectVirtualFile)
      LOG.debug { "content root $projectVirtualFile is added" }
    }
    else {
      LOG.info("content root $projectVirtualFile is not added because" +
               " content roots are already configured (content root count: ${contentRoots.size})")
    }
    model.inheritSdk()
    model.commit()
  }
  finally {
    if (!model.isDisposed()) {
      model.dispose()
    }
  }
  return module
}
