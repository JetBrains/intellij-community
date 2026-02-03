// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity


interface ChangedPropsOrderEntity: WorkspaceEntity {
  val version: Int
  val string: String
  val data: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderDataClass // Change is here, order of entities is changed
  val list: List<Set<Int>>
}

data class ChangedPropsOrderDataClass(val value: Int, val text: String)