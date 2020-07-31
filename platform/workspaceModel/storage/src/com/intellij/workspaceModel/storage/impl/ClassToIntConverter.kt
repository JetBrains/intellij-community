// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.WorkspaceEntity
import java.util.concurrent.atomic.AtomicInteger

internal object ClassToIntConverter {
  private val class2Int = HashBiMap.create<Class<*>, Int>()
  private val idGenerator = AtomicInteger()

  @Synchronized
  fun getInt(clazz: Class<*>): Int = class2Int.getOrPut(clazz) { idGenerator.getAndIncrement() }

  @Synchronized
  fun getClassOrDie(id: Int): Class<*> = class2Int.inverse().getValue(id)
}

internal fun Class<*>.toClassId(): Int = ClassToIntConverter.getInt(this)
internal inline fun <reified E> Int.findEntityClass(): Class<E> = ClassToIntConverter.getClassOrDie(this) as Class<E>
internal fun Int.findWorkspaceEntity(): Class<WorkspaceEntity> = ClassToIntConverter.getClassOrDie(this) as Class<WorkspaceEntity>

