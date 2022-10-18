// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.SymbolicEntityId
import org.jetbrains.deft.annotations.Open
import java.io.Serializable

data class ModuleId(val name: String) : SymbolicEntityId<ModuleEntity> {
  override val presentableName: String
    get() = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ModuleId) return false
    return name == other.name
  }

  override fun hashCode(): Int  = name.hashCode()
}

data class FacetId(val name: String, val type: String, val parentId: ModuleId) : SymbolicEntityId<FacetEntity> {
  override val presentableName: String
    get() = name
}

data class ArtifactId(val name: String) : SymbolicEntityId<ArtifactEntity> {
  override val presentableName: String
    get() = name
}

@Open
sealed class ModuleDependencyItem : Serializable {
  @Open
  sealed class Exportable : ModuleDependencyItem() {
    abstract val exported: Boolean
    abstract val scope: DependencyScope

    abstract fun withScope(scope: DependencyScope): Exportable
    abstract fun withExported(exported: Boolean): Exportable

    data class ModuleDependency(
      val module: ModuleId,
      override val exported: Boolean,
      override val scope: DependencyScope,
      val productionOnTest: Boolean
    ) : Exportable() {
      override fun withScope(scope: DependencyScope): Exportable = copy(scope = scope)
      override fun withExported(exported: Boolean): Exportable = copy(exported = exported)
    }

    data class LibraryDependency(
      val library: LibraryId,
      override val exported: Boolean,
      override val scope: DependencyScope
    ) : Exportable() {
      override fun withScope(scope: DependencyScope): Exportable = copy(scope = scope)
      override fun withExported(exported: Boolean): Exportable = copy(exported = exported)
    }
  }

  //todo use LibraryProxyId to refer to SDK instead
  data class SdkDependency(val sdkName: String, val sdkType: String) : ModuleDependencyItem()

  object InheritedSdkDependency : ModuleDependencyItem()
  object ModuleSourceDependency : ModuleDependencyItem()
  enum class DependencyScope { COMPILE, TEST, RUNTIME, PROVIDED }
}

@Open
sealed class LibraryTableId : Serializable {
  data class ModuleLibraryTableId(val moduleId: ModuleId) : LibraryTableId() {
    override val level: String
      get() = "module"
  }

  object ProjectLibraryTableId : LibraryTableId() {
    override val level: String
      get() = "project"
  }

  data class GlobalLibraryTableId(override val level: String) : LibraryTableId()

  abstract val level: String
}

data class LibraryId(val name: String, val tableId: LibraryTableId) : SymbolicEntityId<LibraryEntity> {
  override val presentableName: String
    get() = name

  @Transient
  private var codeCache: Int = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LibraryId) return false

    if (this.codeCache != 0 && other.codeCache != 0 && this.codeCache != other.codeCache) return false
    if (name != other.name) return false
    if (tableId != other.tableId) return false

    return true
  }

  override fun hashCode(): Int {
    if (codeCache != 0) return codeCache
    var result = name.hashCode()
    result = 31 * result + tableId.hashCode()
    this.codeCache = result
    return result
  }
}