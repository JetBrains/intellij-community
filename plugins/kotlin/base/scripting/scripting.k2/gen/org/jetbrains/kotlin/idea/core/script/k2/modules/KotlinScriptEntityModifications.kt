@file:JvmName("KotlinScriptEntityModifications")

package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface KotlinScriptEntityBuilder : WorkspaceEntityBuilder<KotlinScriptEntity> {
  override var entitySource: EntitySource
  var virtualFileUrl: VirtualFileUrl
  var dependencies: MutableList<KotlinScriptLibraryEntityId>
  var sdk: ModuleDependencyItem
}

internal object KotlinScriptEntityType : EntityType<KotlinScriptEntity, KotlinScriptEntityBuilder>() {
  override val entityClass: Class<KotlinScriptEntity> get() = KotlinScriptEntity::class.java
  operator fun invoke(
    virtualFileUrl: VirtualFileUrl,
    dependencies: List<KotlinScriptLibraryEntityId>,
    sdk: ModuleDependencyItem,
    entitySource: EntitySource,
    init: (KotlinScriptEntityBuilder.() -> Unit)? = null,
  ): KotlinScriptEntityBuilder {
    val builder = builder()
    builder.virtualFileUrl = virtualFileUrl
    builder.dependencies = dependencies.toMutableWorkspaceList()
    builder.sdk = sdk
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyKotlinScriptEntity(
  entity: KotlinScriptEntity,
  modification: KotlinScriptEntityBuilder.() -> Unit,
): KotlinScriptEntity = modifyEntity(KotlinScriptEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createKotlinScriptEntity")
fun KotlinScriptEntity(
  virtualFileUrl: VirtualFileUrl,
  dependencies: List<KotlinScriptLibraryEntityId>,
  sdk: ModuleDependencyItem,
  entitySource: EntitySource,
  init: (KotlinScriptEntityBuilder.() -> Unit)? = null,
): KotlinScriptEntityBuilder = KotlinScriptEntityType(virtualFileUrl, dependencies, sdk, entitySource, init)
