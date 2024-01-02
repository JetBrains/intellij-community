// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.concurrency.annotations.RequiresWriteLock

/**
 * Orphanage - storage that contains content roots/source roots/excludes that currently don't have an associated parent in the storage.
 * These components will be added to the storage in the corresponding parents will appear there.
 * Also, if the parents are already exist in storage, they'll be immediately moved to the original storage.
 *
 * All entities that have the role of temporal parent (it's [ModuleEntity] usually) should have [com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource] entity source.
 *
 * This storage is actively used to store the custom content roots of the modules in case the module is not yet added to the storage.
 *
 * This storage is designed only for described functionality (so you can save only content roots, source roots, and excludes).
 *   However, it may be extended to a general-usage store if needed.
 *
 * ## iml saving format
 * - Entities from orphan storage are not saved in iml files. So, if the original module won't appear in the storage, custom roots will be lost
 *     pros: If the original module was removed without IJ, we won't have an obsolete file
 *     cons: iml file may disappear temporally if the import is not yet finished.
 * - Custom content roots and other elements are saved under AdditionalModuleElements component.
 *   - Orphan storage DOES NOT save entities to this component. However, it's loaded from this component on start.
 * - If we create a custom *source root*, the created content root has [com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource] entity source in orphan storage and
 *     have `dumb="true"` tag in iml file.
 */
interface EntitiesOrphanage {
  val currentSnapshot: ImmutableEntityStorage

  @RequiresWriteLock
  fun update(updater: (MutableEntityStorage) -> Unit)

  companion object {
    const val orphanageKey: String = "ide.workspace.model.separate.component.for.roots"
    val isEnabled: Boolean
      get() = Registry.`is`(orphanageKey, false)

    fun getInstance(project: Project): EntitiesOrphanage = project.service()
  }
}

