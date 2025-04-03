// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


// Use "Generate Workspace Model Implementation" action once interface is updated.
interface KotlinScriptEntity : WorkspaceEntityWithSymbolicId {

    val path: String

    val dependencies: Set<KotlinScriptLibraryId>

    override val symbolicId: KotlinScriptId
        get() = KotlinScriptId(path)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<KotlinScriptEntity> {
    override var entitySource: EntitySource
    var path: String
    var dependencies: MutableSet<KotlinScriptLibraryId>
  }

  companion object : EntityType<KotlinScriptEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      path: String,
      dependencies: Set<KotlinScriptLibraryId>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.path = path
      builder.dependencies = dependencies.toMutableWorkspaceSet()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyKotlinScriptEntity(
  entity: KotlinScriptEntity,
  modification: KotlinScriptEntity.Builder.() -> Unit,
): KotlinScriptEntity {
  return modifyEntity(KotlinScriptEntity.Builder::class.java, entity, modification)
}
//endregion

data class KotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource

data class KotlinScriptId(val path: String) : SymbolicEntityId<KotlinScriptEntity> {
    override val presentableName: String
        get() = path
}
