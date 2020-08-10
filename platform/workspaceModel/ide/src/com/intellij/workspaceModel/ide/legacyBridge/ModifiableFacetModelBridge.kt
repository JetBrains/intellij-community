// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.facet.ModifiableFacetModel
import com.intellij.workspaceModel.storage.WorkspaceEntity

interface ModifiableFacetModelBridge: ModifiableFacetModel {
  fun prepareForCommit()
  fun populateFacetManager(replaceMap: Map<WorkspaceEntity, WorkspaceEntity>)
}