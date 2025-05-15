// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
object ProjectRootEntitySource : EntitySource

/**
 * Used for creating initial state for Files view. [root] will be the root that Files view display
 */
@Internal
interface ProjectRootEntity: WorkspaceEntity {
  val root: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ProjectRootEntity> {
    override var entitySource: EntitySource
    var root: VirtualFileUrl
  }

  companion object : EntityType<ProjectRootEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      root: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.root = root
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
@Internal
fun MutableEntityStorage.modifyProjectRootEntity(
  entity: ProjectRootEntity,
  modification: ProjectRootEntity.Builder.() -> Unit,
): ProjectRootEntity {
  return modifyEntity(ProjectRootEntity.Builder::class.java, entity, modification)
}
//endregion
