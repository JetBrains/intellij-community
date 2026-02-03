// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EclipseProjectPropertiesEntityModifications")

package org.jetbrains.idea.eclipse.config

import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface EclipseProjectPropertiesEntityBuilder : WorkspaceEntityBuilder<EclipseProjectPropertiesEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var variablePaths: Map<String, String>
  var eclipseUrls: MutableList<VirtualFileUrl>
  var unknownCons: MutableList<String>
  var knownCons: MutableList<String>
  var forceConfigureJdk: Boolean
  var expectedModuleSourcePlace: Int
  var srcPlace: Map<String, Int>
}

internal object EclipseProjectPropertiesEntityType : EntityType<EclipseProjectPropertiesEntity, EclipseProjectPropertiesEntityBuilder>() {
  override val entityClass: Class<EclipseProjectPropertiesEntity> get() = EclipseProjectPropertiesEntity::class.java
  operator fun invoke(
    variablePaths: Map<String, String>,
    eclipseUrls: List<VirtualFileUrl>,
    unknownCons: List<String>,
    knownCons: List<String>,
    forceConfigureJdk: Boolean,
    expectedModuleSourcePlace: Int,
    srcPlace: Map<String, Int>,
    entitySource: EntitySource,
    init: (EclipseProjectPropertiesEntityBuilder.() -> Unit)? = null,
  ): EclipseProjectPropertiesEntityBuilder {
    val builder = builder()
    builder.variablePaths = variablePaths
    builder.eclipseUrls = eclipseUrls.toMutableWorkspaceList()
    builder.unknownCons = unknownCons.toMutableWorkspaceList()
    builder.knownCons = knownCons.toMutableWorkspaceList()
    builder.forceConfigureJdk = forceConfigureJdk
    builder.expectedModuleSourcePlace = expectedModuleSourcePlace
    builder.srcPlace = srcPlace
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEclipseProjectPropertiesEntity(
  entity: EclipseProjectPropertiesEntity,
  modification: EclipseProjectPropertiesEntityBuilder.() -> Unit,
): EclipseProjectPropertiesEntity = modifyEntity(EclipseProjectPropertiesEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.eclipseProperties: EclipseProjectPropertiesEntityBuilder?
  by WorkspaceEntity.extensionBuilder(EclipseProjectPropertiesEntity::class.java)


@JvmOverloads
@JvmName("createEclipseProjectPropertiesEntity")
fun EclipseProjectPropertiesEntity(
  variablePaths: Map<String, String>,
  eclipseUrls: List<VirtualFileUrl>,
  unknownCons: List<String>,
  knownCons: List<String>,
  forceConfigureJdk: Boolean,
  expectedModuleSourcePlace: Int,
  srcPlace: Map<String, Int>,
  entitySource: EntitySource,
  init: (EclipseProjectPropertiesEntityBuilder.() -> Unit)? = null,
): EclipseProjectPropertiesEntityBuilder = EclipseProjectPropertiesEntityType(variablePaths,
                                                                              eclipseUrls,
                                                                              unknownCons,
                                                                              knownCons,
                                                                              forceConfigureJdk,
                                                                              expectedModuleSourcePlace,
                                                                              srcPlace,
                                                                              entitySource,
                                                                              init)
