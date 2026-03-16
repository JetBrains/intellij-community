// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TestModulePropertiesEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface TestModulePropertiesEntityBuilder : WorkspaceEntityBuilder<TestModulePropertiesEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var productionModuleId: ModuleId
}

internal object TestModulePropertiesEntityType : EntityType<TestModulePropertiesEntity, TestModulePropertiesEntityBuilder>() {
  override val entityClass: Class<TestModulePropertiesEntity> get() = TestModulePropertiesEntity::class.java
  operator fun invoke(
    productionModuleId: ModuleId,
    entitySource: EntitySource,
    init: (TestModulePropertiesEntityBuilder.() -> Unit)? = null,
  ): TestModulePropertiesEntityBuilder {
    val builder = builder()
    builder.productionModuleId = productionModuleId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    productionModuleId: ModuleId,
    entitySource: EntitySource,
    init: (TestModulePropertiesEntity.Builder.() -> Unit)? = null,
  ): TestModulePropertiesEntity.Builder {
    val builder = builder() as TestModulePropertiesEntity.Builder
    builder.productionModuleId = productionModuleId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyTestModulePropertiesEntity(
  entity: TestModulePropertiesEntity,
  modification: TestModulePropertiesEntityBuilder.() -> Unit,
): TestModulePropertiesEntity = modifyEntity(TestModulePropertiesEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createTestModulePropertiesEntity")
fun TestModulePropertiesEntity(
  productionModuleId: ModuleId,
  entitySource: EntitySource,
  init: (TestModulePropertiesEntityBuilder.() -> Unit)? = null,
): TestModulePropertiesEntityBuilder = TestModulePropertiesEntityType(productionModuleId, entitySource, init)
