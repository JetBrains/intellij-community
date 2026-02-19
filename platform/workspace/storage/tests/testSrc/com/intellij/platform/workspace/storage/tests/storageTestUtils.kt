// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency

fun EntityStorage.checkConsistency() {
  if (this is ImmutableEntityStorageImpl) {
    this.assertConsistency()
    return
  }

  if (this is MutableEntityStorageImpl) {
    this.assertConsistency()
    return
  }
}

internal fun createEmptyBuilder(): MutableEntityStorageImpl {
  return MutableEntityStorageImpl(ImmutableEntityStorageImpl.EMPTY)
}

internal fun createBuilderFrom(storage: EntityStorage): MutableEntityStorageImpl {
  val immutable = when (storage) {
    is ImmutableEntityStorage -> storage
    is MutableEntityStorage -> storage.toSnapshot()
    else -> error("Unexpected storage: $storage")
  }
  return MutableEntityStorageImpl(immutable as ImmutableEntityStorageImpl)
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
