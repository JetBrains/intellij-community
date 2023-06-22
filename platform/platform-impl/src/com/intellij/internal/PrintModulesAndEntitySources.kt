// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity

class PrintModulesAndEntitySources : DumbAwareAction("Print Modules and Entity Sources to Log") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      log.info("Project is null, can't print modules")
      return
    }

    val snap = project.workspaceModel.currentSnapshot
    snap.entities(ModuleEntity::class.java).forEach { module ->
      log.info("Module: ${module.name}, source: ${module.entitySource}")
      module.contentRoots.forEach { contentRootEntity ->
        log.info(" - ContentRoot: ${contentRootEntity.url}, source: ${contentRootEntity.entitySource}")
        contentRootEntity.sourceRoots.forEach { sourceRootEntity ->
          log.info("   - SourceRoot: ${sourceRootEntity.url}, source: ${sourceRootEntity.entitySource}")
        }
        contentRootEntity.excludedUrls.forEach { exclude ->
          log.info("   - ExcludeRoot: ${exclude.url}, source: ${exclude.entitySource}")
        }
      }
    }
  }

  companion object {
    val log: Logger = logger<PrintModulesAndEntitySources>()
  }
}