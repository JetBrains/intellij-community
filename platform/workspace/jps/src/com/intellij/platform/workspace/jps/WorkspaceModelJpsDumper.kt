// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage

@Suppress("unused")
object WorkspaceModelJpsDumper {
  fun dumpModuleStructure(storage: EntityStorage): String {
    return buildString {
      storage.entities(ModuleEntity::class.java).forEach { module ->
        this.appendLine("Module: ${module.name}")
        module.contentRoots.forEach { contentRoot ->
          this.appendLine("  - Content root: ...${contentRoot.url.presentableUrl.takeLast(70)}")
          contentRoot.sourceRoots.forEach { sourceRoot ->
            this.appendLine("    - Source root: ...${sourceRoot.url.presentableUrl.takeLast(70)}")
          }
        }
      }
    }
  }
}
