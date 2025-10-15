// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EntityWithSoftLinksModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface EntityWithSoftLinksBuilder : WorkspaceEntityBuilder<EntityWithSoftLinks> {
  override var entitySource: EntitySource
  var link: OneSymbolicId
  var manyLinks: MutableList<OneSymbolicId>
  var optionalLink: OneSymbolicId?
  var inContainer: Container
  var inOptionalContainer: Container?
  var inContainerList: MutableList<Container>
  var deepContainer: MutableList<TooDeepContainer>
  var sealedContainer: SealedContainer
  var listSealedContainer: MutableList<SealedContainer>
  var justProperty: String
  var justNullableProperty: String?
  var justListProperty: MutableList<String>
  var deepSealedClass: DeepSealedOne
  var children: List<SoftLinkReferencedChildBuilder>
}

internal object EntityWithSoftLinksType : EntityType<EntityWithSoftLinks, EntityWithSoftLinksBuilder>() {
  override val entityClass: Class<EntityWithSoftLinks> get() = EntityWithSoftLinks::class.java
  operator fun invoke(
    link: OneSymbolicId,
    manyLinks: List<OneSymbolicId>,
    inContainer: Container,
    inContainerList: List<Container>,
    deepContainer: List<TooDeepContainer>,
    sealedContainer: SealedContainer,
    listSealedContainer: List<SealedContainer>,
    justProperty: String,
    justListProperty: List<String>,
    deepSealedClass: DeepSealedOne,
    entitySource: EntitySource,
    init: (EntityWithSoftLinksBuilder.() -> Unit)? = null,
  ): EntityWithSoftLinksBuilder {
    val builder = builder()
    builder.link = link
    builder.manyLinks = manyLinks.toMutableWorkspaceList()
    builder.inContainer = inContainer
    builder.inContainerList = inContainerList.toMutableWorkspaceList()
    builder.deepContainer = deepContainer.toMutableWorkspaceList()
    builder.sealedContainer = sealedContainer
    builder.listSealedContainer = listSealedContainer.toMutableWorkspaceList()
    builder.justProperty = justProperty
    builder.justListProperty = justListProperty.toMutableWorkspaceList()
    builder.deepSealedClass = deepSealedClass
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithSoftLinks(
  entity: EntityWithSoftLinks,
  modification: EntityWithSoftLinksBuilder.() -> Unit,
): EntityWithSoftLinks = modifyEntity(EntityWithSoftLinksBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithSoftLinks")
fun EntityWithSoftLinks(
  link: OneSymbolicId,
  manyLinks: List<OneSymbolicId>,
  inContainer: Container,
  inContainerList: List<Container>,
  deepContainer: List<TooDeepContainer>,
  sealedContainer: SealedContainer,
  listSealedContainer: List<SealedContainer>,
  justProperty: String,
  justListProperty: List<String>,
  deepSealedClass: DeepSealedOne,
  entitySource: EntitySource,
  init: (EntityWithSoftLinksBuilder.() -> Unit)? = null,
): EntityWithSoftLinksBuilder =
  EntityWithSoftLinksType(link, manyLinks, inContainer, inContainerList, deepContainer, sealedContainer, listSealedContainer, justProperty,
                          justListProperty, deepSealedClass, entitySource, init)
