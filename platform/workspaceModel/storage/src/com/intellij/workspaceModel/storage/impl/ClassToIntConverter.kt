// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * This class fits the cases where there is a relatively small amount of classes (<50).
 * The disadvantages are:
 * - Two collections copies
 *
 * N.B. One of the approaches is to generate and assign ids to classes during the code generation.
 */
internal class ClassToIntConverter {
  private val map: AtomicReference<Entry> = AtomicReference(Entry(newMap(), emptyArray()))

  private class Entry(
    val classToInt: Object2IntMap<Class<*>>,
    val intToClass: Array<Class<*>?>,
  )

  fun getInt(clazz: Class<*>): Int {
    while (true) {
      val entry = map.get()
      val result = entry.classToInt.getInt(clazz)
      if (result != -1) return result
      val classId = entry.classToInt.size
      val newEntry = Entry(
        newMap(entry.classToInt).also { it.put(clazz, classId) },
        entry.intToClass.copyExtendAndPut(classId, clazz)
      )
      if (map.compareAndSet(entry, newEntry)) return classId
    }
  }

  fun getClassOrDie(id: Int): Class<*> = map.get().intToClass[id] ?: error("Cannot find class by id: $id")

  fun getMap(): Map<Class<*>, Int> = map.get().classToInt

  fun fromMap(map: Map<Class<*>, Int>) {
    val entry = Entry(
      Object2IntOpenHashMap(map),
      Array<Class<*>?>(map.values.maxOrNull() ?: 0) { null }.also { map.forEach { (clazz, index) -> it[index] = clazz } }
    )
    this.map.set(entry)
  }

  private fun newMap(oldMap: Object2IntMap<Class<*>>? = null): Object2IntOpenHashMap<Class<*>> {
    val newMap = if (oldMap != null) Object2IntOpenHashMap(oldMap) else Object2IntOpenHashMap()
    newMap.defaultReturnValue(-1)
    return newMap
  }

  private inline fun <reified T> Array<T?>.copyExtendAndPut(id: Int, data: T): Array<T?> {
    val thisSize = this.size
    val res = Array(id + 1) { if (it < thisSize) this[it] else null }
    res[id] = data
    return res
  }

  companion object {
    val INSTANCE = ClassToIntConverter()
  }
}

internal fun Class<*>.toClassId(): Int = ClassToIntConverter.INSTANCE.getInt(this)
@Suppress("UNCHECKED_CAST")
internal inline fun <reified E> Int.findEntityClass(): Class<E> = ClassToIntConverter.INSTANCE.getClassOrDie(this) as Class<E>
@Suppress("UNCHECKED_CAST")
internal fun Int.findWorkspaceEntity(): Class<WorkspaceEntity> = ClassToIntConverter.INSTANCE.getClassOrDie(this) as Class<WorkspaceEntity>

