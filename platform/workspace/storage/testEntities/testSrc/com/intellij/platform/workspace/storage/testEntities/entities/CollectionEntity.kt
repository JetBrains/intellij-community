// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity


interface CollectionFieldEntity : WorkspaceEntity {
  val versions: Set<Int>
  val names: List<String>

}
