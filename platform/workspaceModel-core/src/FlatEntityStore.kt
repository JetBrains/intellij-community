package com.intellij.workspace.api

import com.intellij.openapi.util.ModificationTracker
import kotlin.reflect.KClass

/*
data class EntityId(val key: Collection<Aspect>, val parentId: EntityId?)

interface EntityStorage {
*/
/*
  fun <T: Aspect> getChildrenWithAspect(parentId: EntityId?, aspectType: KClass<T>, collector: (EntityId, T) -> Unit)
  fun <T: Aspect, R> getChildrenWithAspect(parentId: EntityId?, aspectType: KClass<T>, mapper: (EntityId, T) -> R): List<R>

  fun <T: Aspect> getChildrenAspects(parentId: EntityId?, aspectType: KClass<T>, collector: (EntityId, T) -> Unit)
  fun <T: Aspect, R> getChildrenAspects(parentId: EntityId?, aspectType: KClass<T>, mapper: (EntityId, T) -> R): List<R>

  fun <T1: Aspect, T2: Aspect> getChildrenWithAspectsPair(
    parentId: EntityId?, aspectType1: KClass<T1>, aspectType2: KClass<T2>, collector: (EntityId, T1, T2) -> Unit)
  fun <T1: Aspect, T2: Aspect, R> getChildrenWithAspectsPair(
    parentId: EntityId?, aspectType1: KClass<T1>, aspectType2: KClass<T2>, mapper: (EntityId, T1, T2) -> R): R

  fun getChildren(parentId: EntityId?, collector: (EntityId) -> Unit)
  fun <R> getChildren(parentId: EntityId?, collector: (EntityId) -> R): List<R>

  fun <T: Aspect> getAspects(aspectType: KClass<T>, collector: (EntityId, T) -> Unit)
  fun <T: Aspect, R> getAspects(aspectType: KClass<T>, mapper: (EntityId, T) -> R): R

  fun <T: Aspect> getEntitiesWithAspect(aspectType: KClass<T>, mapper: (EntityId) -> Unit)
  fun <T: Aspect, R> getEntitiesWithAspect(aspectType: KClass<T>, mapper: (EntityId) -> R): List<R>
*/
/*
  fun <T: Aspect> collectAspects(aspectType: KClass<T>, collector: (EntityId, T) -> Unit)

  fun <T: Aspect> getChildrenWithAspect(parentId: EntityId?, aspectType: KClass<T>): Collection<Pair<EntityId, T>>
  fun <T1: Aspect, T2: Aspect> getChildrenWithAspectsPair(parentId: EntityId?, aspectType1: KClass<T1>, aspectType2: KClass<T2>):
    Collection<Triple<EntityId, T1, T2>>
  fun getChildren(parentId: EntityId?): Collection<EntityId>
  fun <T: Aspect> getChildrenAspects(parentId: EntityId?, aspectType: KClass<T>): Collection<T>
  fun <T: Aspect> getAspect(id: EntityId, aspectType: KClass<T>): T
  fun <T: Aspect> tryGetAspect(id: EntityId, aspectType: KClass<T>): T?

  fun <T: Aspect> getAspects(aspectType: KClass<T>): Collection<Pair<EntityId, T>>
  fun <T: Aspect> getEntitiesWithAspect(aspectType: KClass<T>): Collection<EntityId>
}

interface EntityStorageEx: EntityStorage {
  fun forEachEntity(collector: (EntityId, Collection<Aspect>) -> Unit)
}

interface EntityStorageBuilder {
  fun addEntity(parentId: EntityId?, key: Collection<Aspect>, otherAspects: Collection<Aspect> = emptyList()): EntityId
  fun addEntity(parentId: EntityId?, key: Collection<Aspect>, otherAspects: Aspect): EntityId

  fun addEntity(parentId: EntityId?, key: Aspect, otherAspects: Collection<Aspect> = emptyList()): EntityId
  fun addEntity(parentId: EntityId?, key: Aspect, otherAspects: Aspect): EntityId

  fun addAspect(entityId: EntityId, aspect: Aspect)

  fun addStorage(storage: EntityStorage, appendAspectToAllEntities: Aspect? = null)
  fun addDiff(diffBuilder: EntityStorageDiffBuilder)

  fun toStorage(): EntityStorage

  companion object {
    fun create(): EntityStorageBuilder = EntityStorageBuilderImpl()
  }
}

interface EntityStorageDiffBuilder: ModificationTracker {
  fun isEmpty(): Boolean

  fun addEntity(parentId: EntityId?, key: Collection<Aspect>, otherAspects: Collection<Aspect> = emptyList()): EntityId
  fun removeEntity(id: EntityId)

  //fun addAspect(entityId: EntityId, aspect: Aspect)
  //fun removeAspect(entityId: EntityId, aspect: Aspect)
  fun changeAspect(entityId: EntityId, oldAspect: Aspect?, newAspect: Aspect?)

  fun addDiff(diff: EntityStorageDiffBuilder)

  companion object {
    fun create(): EntityStorageDiffBuilder = EntityStorageDiffBuilderImpl()
  }
}

fun EntityStorage.apply(diffBuilder: EntityStorageDiffBuilder): EntityStorage {
  val builder = EntityStorageBuilder.create()
  builder.addStorage(this)
  builder.addDiff(diffBuilder)
  return builder.toStorage()
}

private data class Entity(
  val id: EntityId,
  val aspects: Collection<Aspect>
)

private class EntityStorageBuilderImpl: EntityStorageBuilder {
  private val map: MutableMap<EntityId?, MutableList<Entity>> = hashMapOf()

  private fun add(parentId: EntityId?, key: Set<Aspect>, aspects: Set<Aspect>): EntityId {
    val id = EntityId(key = key, parentId = parentId)
    val list = map.computeIfAbsent(parentId) { mutableListOf() }
    list.add(Entity(id = id, aspects = aspects))
    return id
  }

  override fun addEntity(parentId: EntityId?, key: Collection<Aspect>, otherAspects: Collection<Aspect>): EntityId {
    val keySet = key.toSet()
    return add(parentId, key = keySet, aspects = if (otherAspects.isEmpty()) keySet else ((key + otherAspects).toSet()))
  }

  override fun addEntity(parentId: EntityId?, key: Collection<Aspect>, otherAspects: Aspect): EntityId =
    add(parentId, key = key.toSet(), aspects = (key + otherAspects).toSet())

  override fun addEntity(parentId: EntityId?, key: Aspect, otherAspects: Collection<Aspect>): EntityId =
    add(parentId, key = setOf(key), aspects = if (otherAspects.isEmpty()) setOf(key) else (otherAspects + key).toSet())

  override fun addEntity(parentId: EntityId?, key: Aspect, otherAspects: Aspect): EntityId =
    add(parentId, key = setOf(key), aspects = setOf(key, otherAspects))

  override fun addAspect(entityId: EntityId, aspect: Aspect) {
    val list = map[entityId.parentId]
    val entity = list?.firstOrNull { it.id == entityId }

    if (entity == null) error("Entity with id=$entityId is not found")

    list.remove(entity)
    list.add(entity.copy(aspects = (entity.aspects + aspect).toSet()))
  }

  override fun addStorage(storage: EntityStorage, appendAspectToAllEntities: Aspect?) {
    val impl = storage as EntityStorageImpl

    for ((parentId, entities) in impl.map) {
      val list = map.getOrPut(parentId) { mutableListOf() }

      // TODO Check for aspect duplicates
      if (appendAspectToAllEntities != null) {
        list.addAll(entities.map { Entity(id = it.id, aspects = it.aspects + appendAspectToAllEntities) })
      } else {
        list.addAll(entities)
      }
    }
  }

  override fun addDiff(diffBuilder: EntityStorageDiffBuilder) {
    val diff = diffBuilder as EntityStorageDiffBuilderImpl

    fun removeChildren(id: EntityId) {
      val children = map.remove(id) ?: return
      for (entity in children) {
        removeChildren(entity.id)
      }
    }

    fun removeEntity(id: EntityId) {
      val list = map[id.parentId]
      val entityToRemove = list?.firstOrNull { entity -> entity.id == id }
      if (entityToRemove != null) {
        list.removeIf { entity -> entity.id == id }
      }

      removeChildren(id)
    }

    nextChange@ for (change in diff.changeLog) {
      when (change) {
        is EntityStorageDiffBuilderImpl.Change.AddEntity -> {
          removeEntity(change.entityId)

          val entity = Entity(id = change.entityId, aspects = change.aspects)
          val list = map.getOrPut(change.entityId.parentId) { mutableListOf() }
          list.add(entity)

          Unit
        }

        is EntityStorageDiffBuilderImpl.Change.RemoveEntity -> removeEntity(change.entityId)

        is EntityStorageDiffBuilderImpl.Change.ChangeEntity -> {
          val list = map.getOrPut(change.entityId.parentId) { mutableListOf() }
          val entity = list.firstOrNull { entity -> entity.id == change.entityId } ?: continue@nextChange

          val newEntity = if (change.oldAspect == null && change.newAspect != null) {
            val newAspectType = change.newAspect.javaClass
            if (entity.aspects.any { it.javaClass == newAspectType }) continue@nextChange

            entity.copy(aspects = entity.aspects + change.newAspect)
          } else if (change.oldAspect != null && change.newAspect == null) {
            val newKey = entity.id.key.filter { it != change.oldAspect }
            val newAspects = entity.aspects.filter { it != change.oldAspect }

            Entity(id = EntityId(key = newKey, parentId = entity.id.parentId), aspects = newAspects)
          } else if (change.oldAspect != null && change.newAspect != null) {
            if (entity.id.key.any { it == change.oldAspect }) {
              val newKey = entity.id.key.filter { it != change.oldAspect } + change.newAspect
              val newAspects = entity.aspects.filter { it != change.oldAspect } + change.newAspect
              Entity(id = EntityId(key = newKey, parentId = entity.id.parentId), aspects = newAspects)
            } else if (entity.aspects.any { it == change.oldAspect }) {
              val newAspects = entity.aspects.filter { it != change.oldAspect } + change.newAspect
              Entity(id = entity.id, aspects = newAspects)
            } else continue@nextChange
          } else if (change.oldAspect == null && change.newAspect == null) continue@nextChange
          else error("Must not be reachable")

          list.removeIf { it.id == entity.id }
          list.add(newEntity)

          Unit
        }
      }.let {  } // exhaustive when
    }
  }

  override fun toStorage(): EntityStorage = EntityStorageImpl(map = map.mapValues { it.value.toList() })
}

private class EntityStorageImpl(val map: Map<EntityId?, List<Entity>>): EntityStorageEx {

  override fun <T : Aspect> collectAspects(aspectType: KClass<T>, collector: (EntityId, T) -> Unit) {
    for (list in map.values) {
      for (entity in list) {
        val aspect = entity.aspects.firstOrNull { it.javaClass == aspectType.java }
        if (aspect != null) {
          @Suppress("UNCHECKED_CAST")
          collector(entity.id, aspect as T)
        }
      }
    }
  }

  override fun <T : Aspect> getChildrenWithAspect(parentId: EntityId?, aspectType: KClass<T>): Collection<Pair<EntityId, T>> =
    map[parentId]
      ?.mapNotNull { entity ->
        val aspect = entity.aspects.firstOrNull { it.javaClass == aspectType.java }
        @Suppress("UNCHECKED_CAST")
        if (aspect != null) entity.id to aspect as T else null
      }
    ?: emptyList()

  override fun <T1 : Aspect, T2 : Aspect> getChildrenWithAspectsPair(parentId: EntityId?,
                                                                     aspectType1: KClass<T1>,
                                                                     aspectType2: KClass<T2>): Collection<Triple<EntityId, T1, T2>> =
    map[parentId]
      ?.mapNotNull { entity ->
        val aspect1 = entity.aspects.firstOrNull { it.javaClass == aspectType1.java }
        val aspect2 = entity.aspects.firstOrNull { it.javaClass == aspectType2.java }

        @Suppress("UNCHECKED_CAST")
        if (aspect1 != null && aspect2 != null) Triple(entity.id, aspect1 as T1, aspect2 as T2) else null
      }
    ?: emptyList()

  override fun getChildren(parentId: EntityId?): Collection<EntityId> = map[parentId]?.map { it.id } ?: emptyList()

  override fun <T : Aspect> getChildrenAspects(parentId: EntityId?, aspectType: KClass<T>): Collection<T> =
    map[parentId]
      ?.mapNotNull { entity ->
        val aspect = entity.aspects.firstOrNull { it.javaClass == aspectType.java }
        @Suppress("UNCHECKED_CAST")
        if (aspect != null) aspect as T else null
      }
    ?: emptyList()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Aspect> getAspect(id: EntityId, aspectType: KClass<T>): T =
    map[id.parentId]
      ?.firstOrNull { entity -> entity.id == id }
      ?.let { entity ->
        val aspect = entity.aspects.firstOrNull { it.javaClass == aspectType.java }
        @Suppress("UNCHECKED_CAST")
        if (aspect != null) aspect as T else null
      }
    ?: error("Entity with id=$id and aspectType=${aspectType.simpleName} is not found")

  @Suppress("UNCHECKED_CAST")
  override fun <T : Aspect> tryGetAspect(id: EntityId, aspectType: KClass<T>): T? {
    val entities = map[id.parentId] ?: return null
    val entity = entities.firstOrNull { it.id == id } ?: return null
    return entity.aspects.firstOrNull { it.javaClass == aspectType.java } as? T
  }

  override fun <T : Aspect> getAspects(aspectType: KClass<T>): Collection<Pair<EntityId, T>> {
    val result = mutableListOf<Pair<EntityId, T>>()

    for (list in map.values) {
      for (entity in list) {
        val aspect = entity.aspects.firstOrNull { it.javaClass == aspectType.java }
        if (aspect != null) {
          @Suppress("UNCHECKED_CAST")
          result.add(entity.id to aspect as T)
        }
      }
    }

    return result
  }

  override fun <T : Aspect> getEntitiesWithAspect(aspectType: KClass<T>): Collection<EntityId> {
    val result = mutableListOf<EntityId>()

    for (list in map.values) {
      for (entity in list) {
        if (entity.aspects.any { it.javaClass == aspectType.java }) {
          result.add(entity.id)
        }
      }
    }

    return result
  }

  override fun forEachEntity(collector: (EntityId, Collection<Aspect>) -> Unit) {
    for (list in map.values) {
      for (entity in list) {
        collector(entity.id, entity.aspects)
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    val otherStorage = other as? EntityStorageImpl ?: return false
    if (map.keys != otherStorage.map.keys) return false

    for ((key, value) in map.entries) {
      if (otherStorage.map.getValue(key).toSet() != value.toSet()) return false
    }

    return true
  }

  // TODO?
  override fun hashCode(): Int = 0
}

private class EntityStorageDiffBuilderImpl: EntityStorageDiffBuilder {
  val changeLog: MutableList<Change> = mutableListOf()

  override fun getModificationCount(): Long = changeLog.size.toLong()
  override fun isEmpty(): Boolean = changeLog.isEmpty()

  override fun addEntity(parentId: EntityId?, key: Collection<Aspect>, otherAspects: Collection<Aspect>): EntityId {
    val id = EntityId(parentId = parentId, key = key)
    changeLog.add(Change.AddEntity(id, HashSet(key) + HashSet(otherAspects)))
    return id
  }

  override fun removeEntity(id: EntityId) {
    changeLog.add(Change.RemoveEntity(id))
  }

  override fun changeAspect(entityId: EntityId, oldAspect: Aspect?, newAspect: Aspect?) {
    changeLog.add(Change.ChangeEntity(entityId, oldAspect, newAspect))
  }

  override fun addDiff(diff: EntityStorageDiffBuilder) {
    changeLog.addAll((diff as EntityStorageDiffBuilderImpl).changeLog)
  }

  internal sealed class Change {
    class AddEntity(val entityId: EntityId, val aspects: Collection<Aspect>): Change()
    class RemoveEntity(val entityId: EntityId): Change()
    class ChangeEntity(val entityId: EntityId, val oldAspect: Aspect?, val newAspect: Aspect?): Change()
  }
}*/
