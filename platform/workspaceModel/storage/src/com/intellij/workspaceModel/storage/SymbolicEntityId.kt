// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.deft.annotations.Abstract

/**
 * A base class for typed hierarchical entity IDs. An implementation must be a data class which contains read-only properties of the following types:
 * * primitive types,
 * * String,
 * * enum,
 * * another data class with properties of the allowed types;
 * * sealed abstract class where all implementations satisfy these requirements.
 */
interface SymbolicEntityId<out E : WorkspaceEntityWithSymbolicId> {
  /** Text which can be shown in an error message if id cannot be resolved */
  val presentableName: @NlsSafe String

  fun resolve(storage: EntityStorage): E? = storage.resolve(this)
  override fun toString(): String
}

@Abstract
interface WorkspaceEntityWithSymbolicId : WorkspaceEntity {
  val symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>
}
