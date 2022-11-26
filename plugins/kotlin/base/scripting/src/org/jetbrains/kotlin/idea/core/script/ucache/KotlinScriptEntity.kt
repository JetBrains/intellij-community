// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child


// Use "Generate Workspace Model Implementation" action once interface is updated.
interface KotlinScriptEntity: WorkspaceEntityWithSymbolicId {

    val path: String

    // Direct link resulting in cascade removal: no script no dependencies.
    val dependencies: List<@Child KotlinScriptLibraryEntity>

    override val symbolicId: ScriptId
        get() = ScriptId(path)

    //region generated code
    @GeneratedCodeApiVersion(1)
    interface Builder : KotlinScriptEntity, WorkspaceEntity.Builder<KotlinScriptEntity>, ObjBuilder<KotlinScriptEntity> {
        override var entitySource: EntitySource
        override var path: String
        override var dependencies: List<KotlinScriptLibraryEntity>
    }

    companion object : Type<KotlinScriptEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(path: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): KotlinScriptEntity {
      val builder = builder()
      builder.path = path
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: KotlinScriptEntity, modification: KotlinScriptEntity.Builder.() -> Unit) =
    modifyEntity(KotlinScriptEntity.Builder::class.java, entity, modification)
//endregion

data class KotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?): EntitySource

data class ScriptId(val path: String) : SymbolicEntityId<KotlinScriptEntity> {
    override val presentableName: String
        get() = path
}
