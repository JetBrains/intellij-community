// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.annotations.Open
import org.jetbrains.annotations.NonNls
import java.io.Serializable

data class ModuleId(val name: @NlsSafe String) : SymbolicEntityId<ModuleEntity> {
  override val presentableName: String
    get() = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ModuleId) return false
    return name == other.name
  }

  override fun hashCode(): Int  = name.hashCode()
}

data class FacetId(val name: @NlsSafe String, val type: FacetEntityTypeId, val parentId: ModuleId) : SymbolicEntityId<FacetEntity> {
  override val presentableName: String
    get() = name
}

@Open
sealed class ModuleDependencyItem : Serializable

data class ModuleDependency(
  val module: ModuleId,
  val exported: Boolean,
  val scope: DependencyScope,
  val productionOnTest: Boolean
) : ModuleDependencyItem()

data class LibraryDependency(
  val library: LibraryId,
  val exported: Boolean,
  val scope: DependencyScope
) : ModuleDependencyItem()

data class SdkDependency(val sdk: SdkId) : ModuleDependencyItem()

object InheritedSdkDependency : ModuleDependencyItem()
object ModuleSourceDependency : ModuleDependencyItem()
enum class DependencyScope { COMPILE, TEST, RUNTIME, PROVIDED }

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

  data class GlobalLibraryTableId(override val level: @NonNls String) : LibraryTableId()

  abstract val level: @NonNls String
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

data class SdkId(val name: @NlsSafe String, val type: @NonNls String) : SymbolicEntityId<SdkEntity> {
  override val presentableName: String
    get() = name
}