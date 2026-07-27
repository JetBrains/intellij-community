// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExtensionChildEntityModifications")

package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.devkit.workspaceModel.jsonDump.impl.ExtensionChildEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ExtensionChildEntityBuilder : WorkspaceEntityBuilder<ExtensionChildEntity> {
  override var entitySource: EntitySource
  var extensionChildName: String
  var parent: BaseTestEntityBuilder
  var listOfUrls: MutableList<VirtualFileUrl>
}

internal object ExtensionChildEntityType : EntityType<ExtensionChildEntity, ExtensionChildEntityBuilder>() {
  override val entityClass: Class<ExtensionChildEntity> get() = ExtensionChildEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = ExtensionChildEntityImpl.Builder::class.java
  operator fun invoke(
    extensionChildName: String,
    listOfUrls: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ExtensionChildEntityBuilder.() -> Unit)? = null,
  ): ExtensionChildEntityBuilder {
    val builder = builder()
    builder.extensionChildName = extensionChildName
    builder.listOfUrls = listOfUrls.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyExtensionChildEntity(
  entity: ExtensionChildEntity,
  modification: ExtensionChildEntityBuilder.() -> Unit,
): ExtensionChildEntity = modifyEntity(ExtensionChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createExtensionChildEntity")
fun ExtensionChildEntity(
  extensionChildName: String,
  listOfUrls: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ExtensionChildEntityBuilder.() -> Unit)? = null,
): ExtensionChildEntityBuilder = ExtensionChildEntityType(extensionChildName, listOfUrls, entitySource, init)
