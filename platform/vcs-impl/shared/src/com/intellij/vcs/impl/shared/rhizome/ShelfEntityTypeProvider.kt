// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.Mixin
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer

class ShelfEntityTypeProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> = listOf(ShelvesTreeRootEntity)
}

abstract class NodeEntity : Entity {
  val children by Children
  val orderInParent by Order

  companion object : Mixin<NodeEntity>(NodeEntity::class.java.name, "com.intellij") {
    val Children = manyRef<NodeEntity>("children")
    val Order = requiredValue("order", Int.serializer())
  }
}

class ShelvesTreeRootEntity(override val eid: EID) : NodeEntity() {
  companion object : DurableEntityType<ShelvesTreeRootEntity>(ShelvesTreeRootEntity::class.java.name, "com.intellij", ::ShelvesTreeRootEntity)
}