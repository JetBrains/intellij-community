// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GradleBuildEntity : WorkspaceEntityWithSymbolicId {
  @Parent
  val externalProject: ExternalProjectEntity
  val externalProjectId: ExternalProjectEntityId

  val name: String
  // URL of the directory containing the settings.gradle(.kts)
  val url: VirtualFileUrl

  override val symbolicId: GradleBuildEntityId
    get() = GradleBuildEntityId(externalProjectId, url)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<GradleBuildEntity> {
    override var entitySource: EntitySource
    var externalProject: ExternalProjectEntity.Builder
    var externalProjectId: ExternalProjectEntityId
    var name: String
    var url: VirtualFileUrl
  }

  companion object : EntityType<GradleBuildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      externalProjectId: ExternalProjectEntityId,
      name: String,
      url: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.externalProjectId = externalProjectId
      builder.name = name
      builder.url = url
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyGradleBuildEntity(
  entity: GradleBuildEntity,
  modification: GradleBuildEntity.Builder.() -> Unit,
): GradleBuildEntity {
  return modifyEntity(GradleBuildEntity.Builder::class.java, entity, modification)
}

var ExternalProjectEntity.Builder.gradleBuilds: List<GradleBuildEntity.Builder>
  by WorkspaceEntity.extensionBuilder(GradleBuildEntity::class.java)
//endregion

val ExternalProjectEntity.gradleBuilds: List<GradleBuildEntity>
  by WorkspaceEntity.extension()