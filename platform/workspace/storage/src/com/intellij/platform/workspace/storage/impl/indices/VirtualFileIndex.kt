// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.indices

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.impl.asString
import com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap
import com.intellij.platform.workspace.storage.impl.containers.Object2LongWithDefaultMap
import com.intellij.platform.workspace.storage.impl.containers.putAll
import com.intellij.platform.workspace.storage.url.MutableVirtualFileUrlIndex
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import kotlinx.collections.immutable.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * EntityId2Vfu may contain these possible variants, due to memory optimization:
 * 1) `PersistentMap<EntityId, Pair<String, VirtualFileUrl>>`
 * 2) `PersistentMap<EntityId, Pair<String, PersistentSet<VirtualFileUrl>>>`
 * 3) `PersistentMap<EntityId, PersistentMap<String, VirtualFileUrl>>`
 * 4) `PersistentMap<EntityId, PersistentMap<String, PersistentSet<VirtualFileUrl>>>`
 *
 * In other words:
 * * `VfuInfo = VirtualFileUrl | PersistentSet<VirtualFileUrl>`
 * * `Property2VfuInfo = Pair<String, VfuInfo> | PersistentMap<String, VfuInfo>`
 * * `EntityId2Vfu = PersistentMap<EntityId, Property2VfuInfo>`
 */
internal typealias EntityId2Vfu = PersistentMap<Long, Any>
internal typealias Vfu2EntityId = Object2ObjectOpenCustomHashMap<VirtualFileUrl, Object2LongWithDefaultMap<EntityIdWithProperty>>
internal typealias EntityId2JarDir = BidirectionalLongMultiMap<VirtualFileUrl>

@Suppress("UNCHECKED_CAST")
@ApiStatus.Internal
public open class VirtualFileIndex internal constructor(
  internal open val entityId2VirtualFileUrl: EntityId2Vfu,
  internal open val vfu2EntityId: Vfu2EntityId,
  internal open val entityId2JarDir: EntityId2JarDir,
) : VirtualFileUrlIndex {
  private lateinit var entityStorage: AbstractEntityStorage

  internal constructor() : this(persistentHashMapOf(), Vfu2EntityId(getHashingStrategy()), EntityId2JarDir())

  internal fun getVirtualFiles(id: EntityId): Set<VirtualFileUrl> {
    return entityId2VirtualFileUrl[id]?.let { property2VirtualFileUrlValue ->
      when (property2VirtualFileUrlValue) {
        is Map<*, *> -> property2VirtualFileUrlValue.values.fold(HashSet()) { result, value -> result.addAll(getVirtualFileUrl(value!!)); result }
        is Pair<*, *> -> getVirtualFileUrl(property2VirtualFileUrlValue.second!!)
        else -> throw WrongProperty2VfuInfoTypeException(property2VirtualFileUrlValue::class.java.canonicalName)
      }
    } ?: emptySet()
  }

  internal fun getVirtualFileUrlInfoByEntityId(id: EntityId): Map<String, Set<VirtualFileUrl>> {
    return entityId2VirtualFileUrl[id]?.let { property2VirtualFileUrlValue ->
      when (property2VirtualFileUrlValue) {
        is Map<*, *> -> property2VirtualFileUrlValue.entries.fold(HashMap()) { result, it ->
          result[it.key as String] = getVirtualFileUrl(it.value!!)
          result
        }
        is Pair<*, *> -> mapOf(property2VirtualFileUrlValue.first as String to getVirtualFileUrl(property2VirtualFileUrlValue.second!!))
        else -> throw WrongProperty2VfuInfoTypeException(property2VirtualFileUrlValue::class.java.canonicalName)
      }
    } ?: emptyMap()
  }

  private fun getVirtualFileUrl(value: Any): Set<VirtualFileUrl> {
    return when (value) {
      is Set<*> -> value as Set<VirtualFileUrl>
      is VirtualFileUrl -> setOf(value)
      else -> throw WrongVfuInfoTypeException(value::class.java.canonicalName)
    }
  }

  override fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<WorkspaceEntity> = vfu2EntityId[fileUrl]?.asSequence()?.mapNotNull {
    val entityData = entityStorage.entityDataById(it.value) ?: return@mapNotNull null
    entityData.createEntity(entityStorage)
  } ?: emptySequence()

  public fun findEntitiesToPropertyNameByUrl(fileUrl: VirtualFileUrl): Sequence<Pair<WorkspaceEntity, String>> = vfu2EntityId[fileUrl]?.asSequence()?.mapNotNull {
    val entityData = entityStorage.entityDataById(it.value) ?: return@mapNotNull null
    entityData.createEntity(entityStorage) to it.key.propertyName
  } ?: emptySequence()

  public fun getIndexedJarDirectories(): Set<VirtualFileUrl> = entityId2JarDir.values

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  internal fun assertConsistency() {
    val existingVfuInFirstMap = HashSet<VirtualFileUrl>()
    entityId2VirtualFileUrl.forEach { (entityId, property2Vfu) ->
      fun assertProperty2Vfu(property: String, vfus: Any) {
        val vfuSet = if (vfus is Set<*>) (vfus as Set<VirtualFileUrl>) else setOf(vfus as VirtualFileUrl)
        for (vfu in vfuSet) {
          existingVfuInFirstMap.add(vfu)
          val property2EntityId = this.vfu2EntityId[vfu]
          assert(property2EntityId != null) {
            "VirtualFileUrl: $vfu exists in the first collection by EntityId: ${entityId.asString()} with Property: $property but absent at other"
          }

          val compositeKey = getCompositeKey(entityId, property)
          val existingEntityId = property2EntityId!!.contains(compositeKey)
          assert(existingEntityId) {
            "VirtualFileUrl: $vfu exist in both maps but EntityId: ${entityId.asString()} with Property: $property absent at other"
          }
        }
      }

      when (property2Vfu) {
        is Map<*, *> -> property2Vfu.forEach { (property, vfus) -> assertProperty2Vfu(property as String, vfus!!) }
        is Pair<*, *> -> assertProperty2Vfu(property2Vfu.first as String, property2Vfu.second!!)
        else -> throw WrongProperty2VfuInfoTypeException(property2Vfu::class.java.canonicalName)
      }
    }
    val existingVfuISecondMap = this.vfu2EntityId.keys
    assert(existingVfuInFirstMap.size == existingVfuISecondMap.size) {
      "Different count of VirtualFileUrls EntityId2VirtualFileUrl: ${existingVfuInFirstMap.size} Vfu2EntityId: ${existingVfuISecondMap.size}"
    }
    existingVfuInFirstMap.removeAll(existingVfuISecondMap)
    assert(existingVfuInFirstMap.isEmpty()) { "Both maps contain the same amount of VirtualFileUrls but they are different" }
  }

  internal fun getCompositeKey(entityId: EntityId, propertyName: String) = EntityIdWithProperty(entityId, propertyName)

  public class MutableVirtualFileIndex private constructor(
    /**
     * `@Suppress("RedundantVisibilityModifier")` is used to keep the `internal` modifier and make ApiChecker happy.
     * Otherwise, it thinks that these fields are exposed to the public.
     * This suppression can be removed once the IJ platform migrates to kotlin 2.0
     *
     * Do not write to [entityId2VirtualFileUrl] and [vfu2EntityId] directly! Create a dedicated method for that
     * and call [startWrite] before write.
     */
    @Suppress("RedundantVisibilityModifier") internal override var entityId2VirtualFileUrl: PersistentMap<Long, Any>,
    @Suppress("RedundantVisibilityModifier") internal override var vfu2EntityId: Vfu2EntityId,
    @Suppress("RedundantVisibilityModifier") internal override var entityId2JarDir: EntityId2JarDir,
  ) : VirtualFileIndex(entityId2VirtualFileUrl, vfu2EntityId, entityId2JarDir), MutableVirtualFileUrlIndex {


    private var freezed = true

    @Synchronized
    override fun index(entity: WorkspaceEntity.Builder<out WorkspaceEntity>, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
      index(entity.asBase().id, propertyName, virtualFileUrl)
    }

    @Synchronized
    internal fun index(id: EntityId, propertyName: String, virtualFileUrls: Collection<VirtualFileUrl>) {
      startWrite()
      val newVirtualFileUrls = HashSet(virtualFileUrls)

      fun cleanExistingVfu(existingVfu: Any): Any? {
        when (existingVfu) {
          is Set<*> -> {
            existingVfu as PersistentSet<VirtualFileUrl>
            val newVfu = existingVfu.mutate {
              it.removeIf { vfu ->
                val elementRemoved = newVirtualFileUrls.remove(vfu)
                if (!elementRemoved) removeFromVfu2EntityIdMap(id, propertyName, vfu)
                return@removeIf !elementRemoved
              }
            }
            if (newVfu.isEmpty()) return null
            if (newVfu.size == 1) return newVfu.single()
            return newVfu
          }
          is VirtualFileUrl -> {
            val elementRemoved = newVirtualFileUrls.remove(existingVfu)
            if (!elementRemoved) {
              removeFromVfu2EntityIdMap(id, propertyName, existingVfu)
              return null
            }
            return existingVfu
          }
          else -> throw WrongVfuInfoTypeException(existingVfu::class.java.canonicalName)
        }
      }

      val property2Vfu = entityId2VirtualFileUrl[id]
      if (property2Vfu != null) {
        when (property2Vfu) {
          is Map<*, *> -> {
            property2Vfu as PersistentMap<String, Any>
            val existingVfu = property2Vfu[propertyName]
            if (existingVfu != null) {
              val cleanedVfu = cleanExistingVfu(existingVfu)
              val newPropertyVfu = if (cleanedVfu == null) {
                property2Vfu.remove(propertyName)
              } else {
                property2Vfu.put(propertyName, cleanedVfu)
              }
              if (newPropertyVfu.isEmpty()) {
                entityId2VirtualFileUrl = entityId2VirtualFileUrl.remove(id)
              } else {
                entityId2VirtualFileUrl = entityId2VirtualFileUrl.put(id, newPropertyVfu)
              }
            }
          }
          is Pair<*, *> -> {
            val existingPropertyName = property2Vfu.first as String
            if (existingPropertyName == propertyName) {
              val cleanedVfu = cleanExistingVfu(property2Vfu.second!!)
              if (cleanedVfu == null) {
                entityId2VirtualFileUrl = entityId2VirtualFileUrl.remove(id)
              } else {
                entityId2VirtualFileUrl = entityId2VirtualFileUrl.put(id, Pair(propertyName, cleanedVfu))
              }
              
            }
          }
          else -> throw WrongProperty2VfuInfoTypeException(property2Vfu::class.java.canonicalName)
        }
      }

      newVirtualFileUrls.forEach { indexVirtualFileUrl(id, propertyName, it) }
    }

    @Synchronized
    internal fun indexJarDirectories(id: EntityId, virtualFileUrls: Set<VirtualFileUrl>) {
      entityId2JarDir.removeKey(id)
      if (virtualFileUrls.isEmpty()) return
      virtualFileUrls.forEach { entityId2JarDir.put(id, it) }
    }

    @Synchronized
    internal fun index(id: EntityId, propertyName: String, virtualFileUrl: VirtualFileUrl? = null) {
      startWrite()
      removeByPropertyFromIndexes(id, propertyName)
      if (virtualFileUrl == null) return
      indexVirtualFileUrl(id, propertyName, virtualFileUrl)
    }

    internal fun updateIndex(oldId: EntityId, newId: EntityId, oldIndex: VirtualFileIndex) {
      oldIndex.getVirtualFileUrlInfoByEntityId(oldId).forEach { (property, vfus) -> index(newId, property, vfus) }
      oldIndex.entityId2JarDir.getValues(oldId).apply { indexJarDirectories(newId, this.toSet()) }
    }

    @Synchronized
    internal fun removeRecordsByEntityId(id: EntityId) {
      startWrite()
      entityId2JarDir.removeKey(id)
      val removedProperty2Vfu = entityId2VirtualFileUrl[id] ?: return
      entityId2VirtualFileUrl = entityId2VirtualFileUrl.remove(id)
      when (removedProperty2Vfu) {
        is Pair<*, *> -> removeFromVfu2EntityIdMap(id, removedProperty2Vfu.first as String, removedProperty2Vfu.second!!)
        is Map<*, *> -> removedProperty2Vfu.forEach { (property, vfu) ->
          removeFromVfu2EntityIdMap(id, property as String, vfu!!)
        }
        else -> throw WrongProperty2VfuInfoTypeException(removedProperty2Vfu::class.java.canonicalName)
      }
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      entityId2VirtualFileUrl = entityId2VirtualFileUrl.clear()
      vfu2EntityId.clear()
      entityId2JarDir.clear()
    }

    @TestOnly
    internal fun copyFrom(another: VirtualFileIndex) {
      startWrite()
      entityId2VirtualFileUrl = another.entityId2VirtualFileUrl
      vfu2EntityId.putAll(another.vfu2EntityId)
      entityId2JarDir.putAll(another.entityId2JarDir)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      vfu2EntityId = copyVfuMap(vfu2EntityId)
      entityId2JarDir = entityId2JarDir.copy()
    }

    public fun toImmutable(): VirtualFileIndex {
      freezed = true
      return VirtualFileIndex(entityId2VirtualFileUrl, vfu2EntityId, entityId2JarDir)
    }

    private fun addVfuToExisting(vfu: Any, virtualFileUrl: VirtualFileUrl): Set<VirtualFileUrl> {
      when (vfu) {
        is Set<*> -> {
          return (vfu as PersistentSet<VirtualFileUrl>).add(virtualFileUrl)
        }
        is VirtualFileUrl -> {
          return persistentHashSetOf(vfu, virtualFileUrl)
        }
        else -> throw WrongVfuInfoTypeException(vfu::class.java.canonicalName)
      }
    }

    private fun indexVirtualFileUrl(id: EntityId, propertyName: String, virtualFileUrl: VirtualFileUrl) {
      val property2Vfu = entityId2VirtualFileUrl[id]
      
      if (property2Vfu != null) {
        val newProperty2Vfu: Any = when (property2Vfu) {
          is Map<*, *> -> {
            property2Vfu as PersistentMap<String, Any>
            val vfu = property2Vfu[propertyName]
            if (vfu == null) {
              property2Vfu.put(propertyName, virtualFileUrl)
            }
            else {
              val newVfu = addVfuToExisting(vfu, virtualFileUrl)
              property2Vfu.put(propertyName, newVfu)
            }
          }
          is Pair<*, *> -> {
            property2Vfu as Pair<String, Any>
            if (property2Vfu.first != propertyName) {
              persistentHashMapOf(property2Vfu, propertyName to virtualFileUrl)
            }
            else {
              val newVfu = addVfuToExisting(property2Vfu.second, virtualFileUrl)
              Pair(propertyName, newVfu)
            }
          }
          else -> throw WrongProperty2VfuInfoTypeException(property2Vfu::class.java.canonicalName)
        }
        entityId2VirtualFileUrl = entityId2VirtualFileUrl.put(id, newProperty2Vfu)
      }
      else {
        entityId2VirtualFileUrl = entityId2VirtualFileUrl.put(id, Pair(propertyName, virtualFileUrl))
      }

      val property2EntityId = vfu2EntityId.getOrDefault(virtualFileUrl, Object2LongWithDefaultMap())
      property2EntityId.put(getCompositeKey(id, propertyName), id)
      vfu2EntityId[virtualFileUrl] = property2EntityId
    }

    private fun removeByPropertyFromIndexes(id: EntityId, propertyName: String) {
      val property2vfu = entityId2VirtualFileUrl[id] ?: return
      when (property2vfu) {
        is Map<*, *> -> {
          property2vfu as PersistentMap<String, Any>
          val removedVfu = property2vfu[propertyName] ?: return
          val newProperty2Vfu = property2vfu.remove(propertyName)
          if (newProperty2Vfu.isEmpty()) {
            entityId2VirtualFileUrl = entityId2VirtualFileUrl.remove(id)
          } else {
            entityId2VirtualFileUrl = entityId2VirtualFileUrl.put(id, newProperty2Vfu)
          }
          removeFromVfu2EntityIdMap(id, propertyName, removedVfu)
        }
        is Pair<*, *> -> {
          val existingPropertyName = property2vfu.first as String
          if (existingPropertyName != propertyName) return
          entityId2VirtualFileUrl = entityId2VirtualFileUrl.remove(id)
          removeFromVfu2EntityIdMap(id, propertyName, property2vfu.second!!)
        }
        else -> throw WrongProperty2VfuInfoTypeException(property2vfu::class.java.canonicalName)
      }
    }

    private fun removeFromVfu2EntityIdMap(id: EntityId, property: String, vfus: Any) {
      when (vfus) {
        is Set<*> -> vfus.forEach { removeFromVfu2EntityIdMap(id, property, it as VirtualFileUrl) }
        is VirtualFileUrl -> removeFromVfu2EntityIdMap(id, property, vfus)
        else -> throw WrongVfuInfoTypeException(vfus::class.java.canonicalName)
      }
    }

    private fun removeFromVfu2EntityIdMap(id: EntityId, propertyName: String, vfu: VirtualFileUrl) {
      val property2EntityId = vfu2EntityId[vfu]
      if (property2EntityId == null) {
        LOG.error("The record for $id <=> ${vfu} should be available in both maps")
        return
      }
      property2EntityId.removeLong(getCompositeKey(id, propertyName))
      if (property2EntityId.isEmpty()) vfu2EntityId.remove(vfu)
    }

    private fun copyVfuMap(originMap: Vfu2EntityId): Vfu2EntityId {
      val copiedMap = Vfu2EntityId(getHashingStrategy())
      originMap.forEach { (key, value) -> copiedMap[key] = Object2LongWithDefaultMap.from(value) }
      return copiedMap
    }

    public companion object {
      private val LOG = logger<MutableVirtualFileIndex>()
      private const val DEFAULT_COLLECTION_SIZE = 2

      internal const val VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY = "entitySource"
      internal fun from(other: VirtualFileIndex): MutableVirtualFileIndex {
        if (other is MutableVirtualFileIndex) other.freezed = true
        return MutableVirtualFileIndex(other.entityId2VirtualFileUrl, other.vfu2EntityId, other.entityId2JarDir)
      }
    }
  }
}

internal data class EntityIdWithProperty(val entityId: EntityId, val propertyName: String) {
  override fun toString(): String {
    return "${entityId.asString()}_$propertyName"
  }
}

internal fun getHashingStrategy(): Hash.Strategy<VirtualFileUrl> {
  val indexSensitivityEnabled = Registry.`is`("ide.new.project.model.index.case.sensitivity", false)
  if (!indexSensitivityEnabled) return STANDARD_STRATEGY
  if (!SystemInfoRt.isFileSystemCaseSensitive) return CASE_INSENSITIVE_STRATEGY
  return STANDARD_STRATEGY
}

private val STANDARD_STRATEGY: Hash.Strategy<VirtualFileUrl> = object : Hash.Strategy<VirtualFileUrl> {
  override fun equals(firstVirtualFile: VirtualFileUrl?, secondVirtualFile: VirtualFileUrl?): Boolean {
    if (firstVirtualFile === secondVirtualFile) return true
    if (firstVirtualFile == null || secondVirtualFile == null) return false
    return firstVirtualFile == secondVirtualFile
  }

  override fun hashCode(fileUrl: VirtualFileUrl?): Int {
    if (fileUrl == null) return 0
    return fileUrl.hashCode()
  }
}

private val CASE_INSENSITIVE_STRATEGY: Hash.Strategy<VirtualFileUrl> = object : Hash.Strategy<VirtualFileUrl> {
  override fun equals(firstVirtualFile: VirtualFileUrl?, secondVirtualFile: VirtualFileUrl?): Boolean {
    return StringUtilRt.equal(firstVirtualFile?.url, secondVirtualFile?.url, false)
  }

  override fun hashCode(fileUrl: VirtualFileUrl?): Int {
    if (fileUrl == null) return 0
    return Strings.stringHashCodeInsensitive(fileUrl.url)
  }
}

public class WrongVfuInfoTypeException(encounteredType: String) :
  RuntimeException("VirtualFileUrl should be stored as ab instance or in a set, but $encounteredType was found")

public class WrongProperty2VfuInfoTypeException(encounteredType: String) :
  RuntimeException("Property to VirtualFileUrl mapping should be stored as a pair or in a map, but $encounteredType was found")
