// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.Object2IntWithDefaultMap
import org.jetbrains.annotations.ApiStatus
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

  fun fromMap(map: Object2IntWithDefaultMap<Class<*>>)

  /**
   * Drops each class that matches [predicate]. The id of a dropped class is never reused.
   */
  fun removeIf(predicate: (Class<*>) -> Boolean)

  companion object {
    fun getInstance(): ClassToIntConverter = INSTANCE

    @TestOnly
    internal fun replaceClassToIntConverter(classToIntConverter: ClassToIntConverter) {
      INSTANCE = classToIntConverter
    }

    private var INSTANCE: ClassToIntConverter = ClassToIntConverterImpl()
  }
}

internal class ClassToIntConverterImpl : ClassToIntConverter {
  private val map: AtomicReference<Entry> = AtomicReference(Entry(Object2IntWithDefaultMap(), emptyArray()))

  private class Entry(
    val classToInt: Object2IntWithDefaultMap<Class<*>>,
    val intToClass: Array<Class<*>?>,
  )

  override fun getInt(clazz: Class<*>): Int {
    while (true) {
      val entry = map.get()
      val result = entry.classToInt.getInt(clazz)
      if (result != -1) return result
      val classId = entry.intToClass.size
      val newEntry = Entry(
        Object2IntWithDefaultMap.from(entry.classToInt).also { it.put(clazz, classId) },
        entry.intToClass.copyExtendAndPut(classId, clazz)
      )
      if (map.compareAndSet(entry, newEntry)) return classId
    }
  }

  override fun getClassOrDie(id: Int): Class<*> = map.get().intToClass[id] ?: error("Cannot find class by id: $id")

  override fun getMap(): Map<Class<*>, Int> = map.get().classToInt.toMap()

  override fun removeIf(predicate: (Class<*>) -> Boolean) {
    while (true) {
      val entry = map.get()
      val removed = entry.classToInt.toMap().filterKeys(predicate)
      if (removed.isEmpty()) return
      val classToInt = Object2IntWithDefaultMap.from(entry.classToInt)
      val intToClass = entry.intToClass.copyOf()
      for ((clazz, classId) in removed) {
        classToInt.removeInt(clazz)
        intToClass[classId] = null
      }
      if (map.compareAndSet(entry, Entry(classToInt, intToClass))) return
    }
  }

  override fun fromMap(map: Object2IntWithDefaultMap<Class<*>>) {
    val entry = Entry(
      map,
      Array<Class<*>?>(map.values.maxOrNull() ?: 0) { null }.also { map.forEach { clazz, index -> it[index] = clazz } }
    )
    this.map.set(entry)
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
@ApiStatus.Internal
public fun Int.findWorkspaceEntity(): Class<WorkspaceEntity> = ClassToIntConverter.getInstance().getClassOrDie(this) as Class<WorkspaceEntity>

/**
 * Drops the entity classes that match [predicate], such as the classes of unloaded modules.
 * A class stays registered while one of [liveStorages] holds an entity of it or a reference that names it.
 * A storage of an unknown implementation keeps every class.
 */
@ApiStatus.Internal
public fun forgetWorkspaceEntityClasses(liveStorages: Collection<EntityStorage>, predicate: (Class<*>) -> Boolean) {
  val inUse = HashSet<Int>()
  for (storage in liveStorages) {
    val impl = storage as? AbstractEntityStorage ?: return
    impl.collectClassIds(inUse)
  }
  ClassToIntConverter.getInstance().removeIf { predicate(it) && it.toClassId() !in inUse }
}

private fun AbstractEntityStorage.collectClassIds(result: MutableSet<Int>) {
  entitiesByType.entityFamilies.forEachIndexed { classId, family ->
    if (family != null && family.size() > 0) result.add(classId)
  }
  val containers = listOf(refs.oneToManyContainer, refs.oneToOneContainer, refs.oneToAbstractManyContainer, refs.abstractOneToOneContainer)
  for (connectionId in containers.flatMap { it.keys }) {
    result.add(connectionId.parentClass)
    result.add(connectionId.childClass)
  }
}

