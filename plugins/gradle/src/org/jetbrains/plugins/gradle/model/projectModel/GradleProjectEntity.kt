// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

interface GradleProjectEntity : WorkspaceEntityWithSymbolicId {
  @Parent
  val build: GradleBuildEntity
  val buildId: GradleBuildEntityId

  val name: String
  // Project path with ':' separators, relatively to the build
  val path: String
  // The path identifying the project relatively to the root build (with ':' separators)
  val identityPath: String
  // URL of the directory containing the build.gradle(.kts)
  val url: VirtualFileUrl

  // Mostly, is the same as `identityPath`, but is different in case of the root project of the root build.
  @get:ApiStatus.Internal
  val linkedProjectId: String

  override val symbolicId: GradleProjectEntityId
    get() = GradleProjectEntityId(buildId, url)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<GradleProjectEntity> {
    override var entitySource: EntitySource
    var build: GradleBuildEntity.Builder
    var buildId: GradleBuildEntityId
    var name: String
    var path: String
    var identityPath: String
    var url: VirtualFileUrl
    var linkedProjectId: String
  }

  companion object : EntityType<GradleProjectEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      buildId: GradleBuildEntityId,
      name: String,
      path: String,
      identityPath: String,
      url: VirtualFileUrl,
      linkedProjectId: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.buildId = buildId
      builder.name = name
      builder.path = path
      builder.identityPath = identityPath
      builder.url = url
      builder.linkedProjectId = linkedProjectId
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyGradleProjectEntity(
  entity: GradleProjectEntity,
  modification: GradleProjectEntity.Builder.() -> Unit,
): GradleProjectEntity {
  return modifyEntity(GradleProjectEntity.Builder::class.java, entity, modification)
}
//endregion
