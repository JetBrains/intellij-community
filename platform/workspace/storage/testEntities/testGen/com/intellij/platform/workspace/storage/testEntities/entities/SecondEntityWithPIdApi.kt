// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModifiableSecondEntityWithPId : ModifiableWorkspaceEntity<SecondEntityWithPId> {
  override var entitySource: EntitySource
  var data: String
}

internal object SecondEntityWithPIdType : EntityType<SecondEntityWithPId, ModifiableSecondEntityWithPId>() {
  override val entityClass: Class<SecondEntityWithPId> get() = SecondEntityWithPId::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSecondEntityWithPId.() -> Unit)? = null,
  ): ModifiableSecondEntityWithPId {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySecondEntityWithPId(
  entity: SecondEntityWithPId,
  modification: ModifiableSecondEntityWithPId.() -> Unit,
): SecondEntityWithPId = modifyEntity(ModifiableSecondEntityWithPId::class.java, entity, modification)

@JvmOverloads
@JvmName("createSecondEntityWithPId")
fun SecondEntityWithPId(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSecondEntityWithPId.() -> Unit)? = null,
): ModifiableSecondEntityWithPId = SecondEntityWithPIdType(data, entitySource, init)
