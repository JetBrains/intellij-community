// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaModuleCompilerOptionsEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.java.workspace.entities.impl.JavaModuleCompilerOptionsEntityImpl
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface JavaModuleCompilerOptionsEntityBuilder : WorkspaceEntityBuilder<JavaModuleCompilerOptionsEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var additionalOptions: MutableList<String>
}

internal object JavaModuleCompilerOptionsEntityType :
  EntityType<JavaModuleCompilerOptionsEntity, JavaModuleCompilerOptionsEntityBuilder>() {
  override val entityClass: Class<JavaModuleCompilerOptionsEntity> get() = JavaModuleCompilerOptionsEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = JavaModuleCompilerOptionsEntityImpl.Builder::class.java
  operator fun invoke(
    additionalOptions: List<String>,
    entitySource: EntitySource,
    init: (JavaModuleCompilerOptionsEntityBuilder.() -> Unit)? = null,
  ): JavaModuleCompilerOptionsEntityBuilder {
    val builder = builder()
    builder.additionalOptions = additionalOptions.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyJavaModuleCompilerOptionsEntity(
  entity: JavaModuleCompilerOptionsEntity,
  modification: JavaModuleCompilerOptionsEntityBuilder.() -> Unit,
): JavaModuleCompilerOptionsEntity = modifyEntity(JavaModuleCompilerOptionsEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.javaCompilerOptions: JavaModuleCompilerOptionsEntityBuilder?
  by WorkspaceEntity.extensionBuilder(JavaModuleCompilerOptionsEntity::class.java)


@JvmOverloads
@JvmName("createJavaModuleCompilerOptionsEntity")
fun JavaModuleCompilerOptionsEntity(
  additionalOptions: List<String>,
  entitySource: EntitySource,
  init: (JavaModuleCompilerOptionsEntityBuilder.() -> Unit)? = null,
): JavaModuleCompilerOptionsEntityBuilder = JavaModuleCompilerOptionsEntityType(additionalOptions, entitySource, init)
