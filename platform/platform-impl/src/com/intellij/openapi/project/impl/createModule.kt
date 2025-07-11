// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

private val LOG: Logger = Logger.getInstance("FakeModule")

@ApiStatus.Internal
fun Ref<Module>.getOrInitializeModule(project: Project, baseDir: VirtualFile): Module {
  if (!this.isNull) {
    return this.get()!!
  }

  val moduleManager = ModuleManager.getInstance(project)
  val modules = moduleManager.modules
  if (modules.isNotEmpty()) {
    this.set(modules[0])
    LOG.info("Fake module cannot be created because modules are already configured (module count: " + modules.size + ")")
    return get()
  }

  // correct module name when opening root of drive as project (RUBY-5181)
  val moduleName = baseDir.getName().replace(":", "")
  val imlName = baseDir.getPath() + "/.idea/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION
  val moduleTypeManager = ModuleTypeManager.getInstance()

  return WriteAction.computeAndWait<Module, Throwable> {
    val module = moduleManager.newModule(imlName, moduleTypeManager?.getDefaultModuleType()?.id ?: "unknown")
    val model = ModuleRootManager.getInstance(module).getModifiableModel()
    try {
      val contentRoots = model.getContentRoots()
      if (contentRoots.size == 0) {
        model.addContentEntry(baseDir)
        LOG.debug("content root $baseDir is added")
      }
      else {
        LOG.info("content root " + baseDir + " is not added because content roots are already configured " +
                 "(content root count: " + contentRoots.size + ")")
      }
      model.inheritSdk()
      model.commit()
    }
    finally {
      if (!model.isDisposed()) {
        model.dispose()
      }
    }
    this@getOrInitializeModule.set(module)
    module
  }

}
