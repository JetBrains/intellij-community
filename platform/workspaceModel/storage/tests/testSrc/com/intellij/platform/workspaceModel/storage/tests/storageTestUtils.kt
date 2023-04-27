// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.EntityStorageSnapshotImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency

fun EntityStorage.checkConsistency() {
  if (this is EntityStorageSnapshotImpl) {
    this.assertConsistency()
    return
  }

  if (this is MutableEntityStorageImpl) {
    this.assertConsistency()
    return
  }
}

internal fun createEmptyBuilder(): MutableEntityStorageImpl {
  return MutableEntityStorageImpl.create()
}

internal fun createBuilderFrom(storage: EntityStorage): MutableEntityStorageImpl {
  return MutableEntityStorageImpl.from(storage)
}

internal inline fun makeBuilder(from: EntityStorage? = null, action: MutableEntityStorage.() -> Unit): MutableEntityStorageImpl {
  val builder = if (from == null) {
    createEmptyBuilder()
  }
  else {
    createBuilderFrom(from)
  }
  builder.action()
  return builder
}
