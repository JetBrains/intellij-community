// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VFUEntity2Modifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface VFUEntity2Builder : WorkspaceEntityBuilder<VFUEntity2> {
  override var entitySource: EntitySource
  var data: String
  var filePath: VirtualFileUrl?
  var directoryPath: VirtualFileUrl
  var notNullRoots: MutableList<VirtualFileUrl>
}

internal object VFUEntity2Type : EntityType<VFUEntity2, VFUEntity2Builder>() {
  override val entityClass: Class<VFUEntity2> get() = VFUEntity2::class.java
  operator fun invoke(
    data: String,
    directoryPath: VirtualFileUrl,
    notNullRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (VFUEntity2Builder.() -> Unit)? = null,
  ): VFUEntity2Builder {
    val builder = builder()
    builder.data = data
    builder.directoryPath = directoryPath
    builder.notNullRoots = notNullRoots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyVFUEntity2(
  entity: VFUEntity2,
  modification: VFUEntity2Builder.() -> Unit,
): VFUEntity2 = modifyEntity(VFUEntity2Builder::class.java, entity, modification)

@JvmOverloads
@JvmName("createVFUEntity2")
fun VFUEntity2(
  data: String,
  directoryPath: VirtualFileUrl,
  notNullRoots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (VFUEntity2Builder.() -> Unit)? = null,
): VFUEntity2Builder = VFUEntity2Type(data, directoryPath, notNullRoots, entitySource, init)
