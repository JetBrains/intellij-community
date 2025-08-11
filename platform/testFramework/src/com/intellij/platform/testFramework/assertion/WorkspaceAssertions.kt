// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.entities

object WorkspaceAssertions {

  inline fun <reified T : WorkspaceEntityWithSymbolicId> assertEntities(
    project: Project,
    expectedIds: List<SymbolicEntityId<T>>,
    noinline messageSupplier: (() -> String)? = null,
  ) {
    assertEntities(project.workspaceModel.currentSnapshot, expectedIds, messageSupplier)
  }

  inline fun <reified T : WorkspaceEntityWithSymbolicId> assertEntities(
    storage: EntityStorage,
    expectedIds: List<SymbolicEntityId<T>>,
    noinline messageSupplier: (() -> String)? = null,
  ) {
    val actualIds = storage.entities<T>().map { it.symbolicId }.toList()
    CollectionAssertions.assertEqualsUnordered(expectedIds, actualIds, messageSupplier)
  }
}