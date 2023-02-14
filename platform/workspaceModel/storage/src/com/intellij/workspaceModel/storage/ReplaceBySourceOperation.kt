// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl

internal interface ReplaceBySourceOperation {
  fun replace(
    targetStorage: MutableEntityStorageImpl,
    replaceWithStorage: AbstractEntityStorage,
    entityFilter: (EntitySource) -> Boolean,
  )
}
