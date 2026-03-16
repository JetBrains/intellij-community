// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.EqualsBy
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

/**
 * Describes a [ContentEntry][com.intellij.openapi.roots.ContentEntry].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface ContentRootEntity : WorkspaceEntity {
  @EqualsBy
  val url: VirtualFileUrl
  val excludedPatterns: List<@NlsSafe String>

  @Parent
  val module: ModuleEntity

  val sourceRoots: List<SourceRootEntity>
  val excludedUrls: List<ExcludeUrlEntity>

  //region generated code
  @Deprecated(message = "Use ContentRootEntityBuilder instead")
  interface Builder : ContentRootEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }
  }

  companion object : EntityType<ContentRootEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      url: VirtualFileUrl,
      excludedPatterns: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ContentRootEntityType.compatibilityInvoke(url, excludedPatterns, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyContentRootEntity(
  entity: ContentRootEntity,
  modification: ContentRootEntity.Builder.() -> Unit,
): ContentRootEntity {
  return modifyEntity(ContentRootEntity.Builder::class.java, entity, modification)
}

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ContentRootEntity.Builder.excludeUrlOrder: ExcludeUrlOrderEntity.Builder?
  get() = (this as ContentRootEntityBuilder).excludeUrlOrder as ExcludeUrlOrderEntity.Builder?
  set(value) {
    (this as ContentRootEntityBuilder).excludeUrlOrder = value
  }

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ContentRootEntity.Builder.sourceRootOrder: SourceRootOrderEntity.Builder?
  get() = (this as ContentRootEntityBuilder).sourceRootOrder as SourceRootOrderEntity.Builder?
  set(value) {
    (this as ContentRootEntityBuilder).sourceRootOrder = value
  }
//endregion

@Parent
val ExcludeUrlEntity.contentRoot: ContentRootEntity? by WorkspaceEntity.extension()


/**
 * Provides an ID of a source root type (`java-source`, `java-resources`, etc.).
 * Use [com.intellij.workspaceModel.ide.legacyBridge.sdk.SourceRootTypeRegistry] to get a descriptor by this ID.
 */
data class SourceRootTypeId(val name: @NonNls String)

/**
 * Describes a [SourceFolder][com.intellij.openapi.roots.SourceFolder].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface SourceRootEntity : WorkspaceEntity {
  val url: VirtualFileUrl
  val rootTypeId: SourceRootTypeId

  @Parent
  val contentRoot: ContentRootEntity

  //region generated code
  @Deprecated(message = "Use SourceRootEntityBuilder instead")
  interface Builder : SourceRootEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getContentRoot(): ContentRootEntity.Builder = contentRoot as ContentRootEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setContentRoot(value: ContentRootEntity.Builder) {
      contentRoot = value
    }
  }

  companion object : EntityType<SourceRootEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      url: VirtualFileUrl,
      rootTypeId: SourceRootTypeId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = SourceRootEntityType.compatibilityInvoke(url, rootTypeId, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifySourceRootEntity(
  entity: SourceRootEntity,
  modification: SourceRootEntity.Builder.() -> Unit,
): SourceRootEntity {
  return modifyEntity(SourceRootEntity.Builder::class.java, entity, modification)
}

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var SourceRootEntity.Builder.customSourceRootProperties: CustomSourceRootPropertiesEntity.Builder?
  get() = (this as SourceRootEntityBuilder).customSourceRootProperties as CustomSourceRootPropertiesEntity.Builder?
  set(value) {
    (this as SourceRootEntityBuilder).customSourceRootProperties = value
  }
//endregion
