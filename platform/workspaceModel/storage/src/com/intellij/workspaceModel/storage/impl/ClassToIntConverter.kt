// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * This class fits the cases where there is a relatively small amount of classes (<50).
 * The disadvantages are:
 * - Two collections copies
 * - The generated id's may contain gaps (some ids may miss, e.g. 1, 2, 4, 5)
 * The problem with gaps affects workspace model because the classes are stored in the list according to
 *   their ids, so with gaps we'll have some memory wasting in the storage.
 * The current plan is to generate and assign ids to classes during the code generation.
 */
internal class ClassToIntConverter {
  private val idGenerator = AtomicInteger()
  private val map: AtomicReference<Entry> = AtomicReference(Entry(newMap(), emptyList()))

  private class Entry(
    val classToInt: Object2IntMap<Class<*>>,
    val intToClass: List<Class<*>?>,
  )

  fun getInt(clazz: Class<*>): Int {
    var classId = -1
    while (true) {
      val entry = map.get()
      val result = entry.classToInt.getInt(clazz)
      if (result != -1) return result
      if (classId == -1) classId = idGenerator.getAndIncrement()
      val newEntry = Entry(
        newMap(entry.classToInt).also { it.put(clazz, classId) },
        ArrayList(entry.intToClass).also { it.extendAndPut(classId, clazz) }
      )
      if (map.compareAndSet(entry, newEntry)) return classId
    }
  }

  fun getClassOrDie(id: Int): Class<*> = map.get().intToClass[id] ?: error("Cannot find class by id: $id")

  fun getMap(): Map<Class<*>, Int> = map.get().classToInt

  fun fromMap(map: Map<Class<*>, Int>) {
    val entry = Entry(
      Object2IntOpenHashMap(map),
      ArrayList<Class<*>?>().also { list -> map.forEach { (clazz, index) -> list.extendAndPut(index, clazz) } }
    )
    this.map.set(entry)
    idGenerator.set((map.map { it.value }.maxOrNull() ?: -1) + 1)
  }

  @TestOnly
  fun clear() {
    map.set(Entry(newMap(), emptyList()))
    idGenerator.set(0)
  }

  private fun newMap(oldMap: Object2IntMap<Class<*>>? = null): Object2IntOpenHashMap<Class<*>> {
    val newMap = if (oldMap != null) Object2IntOpenHashMap(oldMap) else Object2IntOpenHashMap()
    newMap.defaultReturnValue(-1)
    return newMap
  }

  private fun <T> MutableList<T?>.extendAndPut(id: Int, data: T) {
    while (this.size < id + 1) {
      this.add(null)
    }
    this[id] = data
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

