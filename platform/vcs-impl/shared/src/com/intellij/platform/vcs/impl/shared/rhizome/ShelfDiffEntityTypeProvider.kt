// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import fleet.kernel.DurableEntityType
import org.jetbrains.annotations.ApiStatus

/**
 * ShelfDiffEntityTypeProvider is responsible for providing entity types specific to the shelf diff feature.
 */
@ApiStatus.Internal
class ShelfDiffEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(SelectShelveChangeEntity, DiffSplitterEntity)
}

/**
 * Event sent from backend to frontend to synchronize selection between diff and changes tree.
 */
@ApiStatus.Internal
data class SelectShelveChangeEntity(override val eid: EID) : Entity {
  val changeList: ShelvedChangeListEntity by ChangeList
  val change: ShelvedChangeEntity? by Change
  val project: ProjectEntity by Project

  @ApiStatus.Internal
  companion object : DurableEntityType<SelectShelveChangeEntity>(SelectShelveChangeEntity::class.java.name, "com.intellij", ::SelectShelveChangeEntity) {
    val ChangeList: Required<ShelvedChangeListEntity> = requiredRef("ChangeList")
    val Change: Optional<ShelvedChangeEntity> = optionalRef("Change")
    val Project: Required<ProjectEntity> = requiredRef("project", RefFlags.UNIQUE)
  }
}

/**
 * Since we can't insert lux windows inside other components, we can't show the diff in the tool window for RemDev.
 * This workaround was implemented to make it work in monolith.
 * Note: This is not how Rhizome code is supposed to be written!
 */
@ApiStatus.Internal
data class DiffSplitterEntity(override val eid: EID) : Entity {
  val splitter: PreviewDiffSplitterComponent by Splitter
  val project: ProjectEntity by Project

  @ApiStatus.Internal
  companion object : EntityType<DiffSplitterEntity>(DiffSplitterEntity::class.java.name, "com.intellij", ::DiffSplitterEntity) {
    val Splitter: Required<PreviewDiffSplitterComponent> = requiredTransient("splitter")
    val Project: Required<ProjectEntity> = requiredRef("project", RefFlags.UNIQUE)
  }
}