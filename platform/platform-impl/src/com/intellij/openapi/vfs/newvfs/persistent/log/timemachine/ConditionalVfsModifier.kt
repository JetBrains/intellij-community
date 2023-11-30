// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation


/**
 * Mental hint:
```
val ifModifiesParentIdOf42: ConditionalVfsModifier<Int> = VfsModificationContract.parentId.ifModifies.precondition {
  (this as? RecordsOperation<*>)?.fileId == 42
}
val operation: VfsOperation<*> = VfsOperation.RecordsOperation.FillRecord(...)
operation.ifModifiesParentIdOf42 { parentId -> println("new parentId of vfile 42: $parentId") }
```
 */
typealias ConditionalVfsModifier<T> = VfsOperation<*>.(modify: (T) -> Unit) -> Unit

inline fun <T> ConditionalVfsModifier<T>.precondition(
  crossinline condition: VfsOperation<*>.() -> Boolean,
): ConditionalVfsModifier<T> = { modify ->
  if (condition()) {
    this@precondition(modify)
  }
}

inline fun <T> ConditionalVfsModifier<T>.andIf(crossinline condition: VfsOperation<*>.(T) -> Boolean): ConditionalVfsModifier<T> = { modify ->
  val outerMod = this@andIf
  val op = this
  outerMod(op) {
    if (condition(it)) {
      modify(it)
    }
  }
}

inline fun <T, R> ConditionalVfsModifier<T>.map(crossinline f: (T) -> R): ConditionalVfsModifier<R> = { modify ->
  val outerMod = this@map
  val op = this
  outerMod(op) {
    modify(f(it))
  }
}