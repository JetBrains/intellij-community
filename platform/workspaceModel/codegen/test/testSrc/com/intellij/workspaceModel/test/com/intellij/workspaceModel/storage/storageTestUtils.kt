// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.codegen.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.codegen.storage.impl.WorkspaceEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency

fun WorkspaceEntityStorage.checkConsistency() {
  if (this is WorkspaceEntityStorageImpl) {
    this.assertConsistency()
    return
  }

  if (this is WorkspaceEntityStorageBuilderImpl) {
    this.assertConsistency()
    return
  }
}

internal fun createEmptyBuilder(): WorkspaceEntityStorageBuilder {
  return WorkspaceEntityStorageBuilderImpl.create()
}

internal fun createBuilderFrom(storage: WorkspaceEntityStorage): WorkspaceEntityStorageBuilder {
  return WorkspaceEntityStorageBuilderImpl.from(storage)
}

internal inline fun makeBuilder(from: WorkspaceEntityStorage? = null, action: WorkspaceEntityStorageBuilder.() -> Unit): WorkspaceEntityStorageBuilder {
  val builder = if (from == null) {
    createEmptyBuilder()
  }
  else {
    createBuilderFrom(from)
  }
  builder.action()
  return builder
}
