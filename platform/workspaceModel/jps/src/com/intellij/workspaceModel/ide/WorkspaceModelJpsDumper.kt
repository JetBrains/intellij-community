// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

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
