// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.WorkspaceEntity
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

/**
 * This class fits the cases where there is a relatively small amount of classes (<50).
 * The disadvantages are:
 * - Two collections copies
 *
 * N.B. One of the approaches is to generate and assign ids to classes during the code generation.
 */
internal interface ClassToIntConverter {
  fun getInt(clazz: Class<*>): Int

  fun getClassOrDie(id: Int): Class<*>

  fun getMap(): Map<Class<*>, Int>

  fun fromMap(map: Object2IntMap<Class<*>>)

  companion object {
    fun getInstance(): ClassToIntConverter = INSTANCE

    @TestOnly
    internal fun replaceClassToIntConverter(classToIntConverter: ClassToIntConverter) {
      INSTANCE = classToIntConverter
    }

    private var INSTANCE: ClassToIntConverter = ClassToIntConverterImpl()
  }
}

internal class ClassToIntConverterImpl: ClassToIntConverter {
  private val map: AtomicReference<Entry> = AtomicReference(Entry(newMap(), emptyArray()))

  private class Entry(
    val classToInt: Object2IntMap<Class<*>>,
    val intToClass: Array<Class<*>?>,
  )

  override fun getInt(clazz: Class<*>): Int {
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

  override fun getClassOrDie(id: Int): Class<*> = map.get().intToClass[id] ?: error("Cannot find class by id: $id")

  override fun getMap(): Map<Class<*>, Int> = map.get().classToInt

  override fun fromMap(map: Object2IntMap<Class<*>>) {
    val entry = Entry(
      map,
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
}

internal fun Class<*>.toClassId(): Int = ClassToIntConverter.getInstance().getInt(this)
@Suppress("UNCHECKED_CAST")
internal fun Int.findWorkspaceEntity(): Class<WorkspaceEntity> = ClassToIntConverter.getInstance().getClassOrDie(this) as Class<WorkspaceEntity>

