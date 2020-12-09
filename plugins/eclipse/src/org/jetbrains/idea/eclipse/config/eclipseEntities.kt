// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.workspaceModel.ide.JpsFileDependentEntitySource
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.ManyToOne
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneChild
import com.intellij.workspaceModel.storage.impl.references.OneToOneParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

data class EclipseProjectFile(
  val classpathFile: VirtualFileUrl,
  val internalSource: JpsFileEntitySource
) : EntitySource, JpsFileDependentEntitySource {
  override val originalSource: JpsFileEntitySource
    get() = internalSource
}

/**
 * Stores data from [EclipseModuleManagerImpl] in workspace model
 */
class EclipseProjectPropertiesEntity(
  val variablePaths: Map<String, String>,
  val eclipseUrls: Set<VirtualFileUrl>,
  val unknownCons: Set<String>,
  val knownCons: Set<String>,
  val forceConfigureJdk: Boolean,
  val expectedModuleSourcePlace: Int,
  val srcPlace: Map<String, Int>

) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, EclipseProjectPropertiesEntity>(ModuleEntity::class.java)
  }
}

@Suppress("unused") //created via reflection in ClassConversion.entityToEntityData
class EclipseProjectPropertiesEntityData : WorkspaceEntityData<EclipseProjectPropertiesEntity>() {
  lateinit var variablePaths: Map<String, String>
  lateinit var eclipseUrls: Set<VirtualFileUrl>
  lateinit var unknownCons: Set<String>
  lateinit var knownCons: Set<String>
  var forceConfigureJdk = false
  var expectedModuleSourcePlace = 0
  lateinit var srcPlace: Map<String, Int>

  override fun createEntity(snapshot: WorkspaceEntityStorage): EclipseProjectPropertiesEntity {
    return EclipseProjectPropertiesEntity(variablePaths, eclipseUrls, unknownCons, knownCons, forceConfigureJdk, expectedModuleSourcePlace, srcPlace).also { addMetaData(it, snapshot) }
  }
}

class ModifiableEclipseProjectPropertiesEntity : ModifiableWorkspaceEntityBase<EclipseProjectPropertiesEntity>() {
  var variablePaths: MutableMap<String, String> by EntityDataDelegation()
  var eclipseUrls: MutableSet<VirtualFileUrl> by EntityDataDelegation()
  var unknownCons: MutableSet<String> by EntityDataDelegation()
  var knownCons: MutableSet<String> by EntityDataDelegation()
  var forceConfigureJdk: Boolean by EntityDataDelegation()
  var expectedModuleSourcePlace: Int by EntityDataDelegation()
  var srcPlace: MutableMap<String, Int> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(EclipseProjectPropertiesEntity::class.java, ModuleEntity::class.java, true)
}

fun WorkspaceEntityStorageDiffBuilder.addEclipseProjectPropertiesEntity(module: ModuleEntity, source: EntitySource)
  = addEntity(ModifiableEclipseProjectPropertiesEntity::class.java, source) {
  this.module = module
  variablePaths = LinkedHashMap()
  eclipseUrls = LinkedHashSet()
  unknownCons = LinkedHashSet()
  knownCons = LinkedHashSet()
  srcPlace = LinkedHashMap()
}

private val eclipsePropertiesDelegate = OneToOneParent.Nullable<ModuleEntity, EclipseProjectPropertiesEntity>(EclipseProjectPropertiesEntity::class.java, false)
val ModuleEntity.eclipseProperties: EclipseProjectPropertiesEntity? by eclipsePropertiesDelegate

fun ModifiableEclipseProjectPropertiesEntity.setVariable(kind: String, name: String, path: String) {
  variablePaths[kind + path] = name
}

fun EclipseProjectPropertiesEntity.getVariable(kind: String, path: String): String? = variablePaths[kind + path]