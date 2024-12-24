// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GroupingItemTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(GroupingItemsEntity, GroupingItemEntity)
}

/**
 * Represents an entity that groups items.
 *
 * @property place The place associated with the grouping of items.
 * @property items The list of items, *enabled* for this place.
 */
@ApiStatus.Internal
data class GroupingItemsEntity(override val eid: EID) : Entity {
  val place: String by Place
  val items: Set<GroupingItemEntity> by Items

  @ApiStatus.Internal
  companion object : DurableEntityType<GroupingItemsEntity>(GroupingItemsEntity::class.java.name, "com.intellij", ::GroupingItemsEntity) {
    val Place: Required<String> = requiredValue("place", String.serializer())
    val Items: Many<GroupingItemEntity> = manyRef("items", RefFlags.CASCADE_DELETE)
  }
}

@ApiStatus.Internal
data class GroupingItemEntity(override val eid: EID) : Entity {
  val name: String by Name

  @ApiStatus.Internal
  companion object : DurableEntityType<GroupingItemEntity>(GroupingItemEntity::class.java.name, "com.intellij", ::GroupingItemEntity) {
    val Name: Required<String> = requiredValue("name", String.serializer(), Indexing.UNIQUE)
  }
}
