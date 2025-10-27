// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RootsExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

/**
 * Stores order of excluded roots in iml file.
 * This is needed to ensure that corresponding tags are saved in the same order to avoid unnecessary modifications of iml file.
 */
@Internal
interface SourceRootOrderEntity : WorkspaceEntity {
  val orderOfSourceRoots: List<VirtualFileUrl>

  @Parent
  val contentRootEntity: ContentRootEntity

  //region generated code
  @Deprecated(message = "Use SourceRootOrderEntityBuilder instead")
  interface Builder : SourceRootOrderEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getContentRootEntity(): ContentRootEntity.Builder = contentRootEntity as ContentRootEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setContentRootEntity(value: ContentRootEntity.Builder) {
      contentRootEntity = value
    }
  }

  companion object : EntityType<SourceRootOrderEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      orderOfSourceRoots: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = SourceRootOrderEntityType.compatibilityInvoke(orderOfSourceRoots, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifySourceRootOrderEntity(
  entity: SourceRootOrderEntity,
  modification: SourceRootOrderEntity.Builder.() -> Unit,
): SourceRootOrderEntity {
  return modifyEntity(SourceRootOrderEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ContentRootEntity.sourceRootOrder: SourceRootOrderEntity?
  by WorkspaceEntity.extension()


/**
 * Describes custom properties of [SourceFolder][com.intellij.openapi.roots.SourceFolder].
 */
@Internal
interface CustomSourceRootPropertiesEntity : WorkspaceEntity {
  val propertiesXmlTag: @NonNls String

  @Parent
  val sourceRoot: SourceRootEntity

  //region generated code
  @Deprecated(message = "Use CustomSourceRootPropertiesEntityBuilder instead")
  interface Builder : CustomSourceRootPropertiesEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getSourceRoot(): SourceRootEntity.Builder = sourceRoot as SourceRootEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setSourceRoot(value: SourceRootEntity.Builder) {
      sourceRoot = value
    }
  }

  companion object : EntityType<CustomSourceRootPropertiesEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      propertiesXmlTag: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = CustomSourceRootPropertiesEntityType.compatibilityInvoke(propertiesXmlTag, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyCustomSourceRootPropertiesEntity(
  entity: CustomSourceRootPropertiesEntity,
  modification: CustomSourceRootPropertiesEntity.Builder.() -> Unit,
): CustomSourceRootPropertiesEntity {
  return modifyEntity(CustomSourceRootPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val SourceRootEntity.customSourceRootProperties: CustomSourceRootPropertiesEntity?
  by WorkspaceEntity.extension()

/**
 * Stores order of excluded roots in iml file.
 * This is needed to ensure that corresponding tags are saved in the same order to avoid unnecessary modifications of iml file.
 */
@Internal
interface ExcludeUrlOrderEntity : WorkspaceEntity {
  val order: List<VirtualFileUrl>

  @Parent
  val contentRoot: ContentRootEntity

  //region generated code
  @Deprecated(message = "Use ExcludeUrlOrderEntityBuilder instead")
  interface Builder : ExcludeUrlOrderEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getContentRoot(): ContentRootEntity.Builder = contentRoot as ContentRootEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setContentRoot(value: ContentRootEntity.Builder) {
      contentRoot = value
    }
  }

  companion object : EntityType<ExcludeUrlOrderEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      order: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ExcludeUrlOrderEntityType.compatibilityInvoke(order, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyExcludeUrlOrderEntity(
  entity: ExcludeUrlOrderEntity,
  modification: ExcludeUrlOrderEntity.Builder.() -> Unit,
): ExcludeUrlOrderEntity {
  return modifyEntity(ExcludeUrlOrderEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ContentRootEntity.excludeUrlOrder: ExcludeUrlOrderEntity?
  by WorkspaceEntity.extension()