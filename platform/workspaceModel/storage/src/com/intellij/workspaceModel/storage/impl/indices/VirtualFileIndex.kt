// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.util.text.Strings
import com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintMap
import com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintSet
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRoot
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.containers.BidirectionalLongMultiMap
import com.intellij.workspaceModel.storage.impl.containers.putAll
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.annotations.TestOnly
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * EntityId2Vfu may contains these possible variants, due to memory optimization:
 * 1) Object2ObjectOpenHashMap<EntityId, Pair<String, VirtualFileUrl>>
 * 2) Object2ObjectOpenHashMap<EntityId, Pair<String, ObjectOpenHashSet<VirtualFileUrl>>>
 * 3) Object2ObjectOpenHashMap<EntityId, Object2ObjectOpenHashMap<String, VirtualFileUrl>>
 * 4) Object2ObjectOpenHashMap<EntityId, Object2ObjectOpenHashMap<String, ObjectOpenHashSet<VirtualFileUrl>>>
 */
//internal typealias EntityId2Vfu = Object2ObjectOpenHashMap<EntityId, Any>
//internal typealias Vfu2EntityId = Object2ObjectOpenHashMap<VirtualFileUrl, Object2ObjectOpenHashMap<String, EntityId>>
//internal typealias EntityId2JarDir = BidirectionalMultiMap<EntityId, VirtualFileUrl>
internal typealias EntityId2Vfu = Long2ObjectOpenHashMap<Any>
internal typealias Vfu2EntityId = Object2ObjectOpenCustomHashMap<VirtualFileUrl, Object2LongOpenHashMap<String>>
internal typealias EntityId2JarDir = BidirectionalLongMultiMap<VirtualFileUrl>

@Suppress("UNCHECKED_CAST")
open class VirtualFileIndex internal constructor(
  internal open val entityId2VirtualFileUrl: EntityId2Vfu,
  internal open val vfu2EntityId: Vfu2EntityId,
  internal open val entityId2JarDir: EntityId2JarDir,
) : VirtualFileUrlIndex {
  private lateinit var entityStorage: AbstractEntityStorage

  constructor() : this(EntityId2Vfu(), Vfu2EntityId(getHashingStrategy()), EntityId2JarDir())

  internal fun getVirtualFiles(id: EntityId): Set<VirtualFileUrl> {
    val result = mutableSetOf<VirtualFileUrl>()
    entityId2VirtualFileUrl[id]?.also { value ->
      when (value) {
        is Object2ObjectOpenHashMap<*, *> -> value.values.forEach { vfu -> result.addAll(getVirtualFileUrl(vfu)) }
        is Pair<*, *> -> result.addAll(getVirtualFileUrl(value.second!!))
      }
    }
    return result
  }

  internal fun getVirtualFileUrlInfoByEntityId(id: EntityId): Map<String, MutableSet<VirtualFileUrl>> {
    val property2VfuMap = entityId2VirtualFileUrl[id] ?: return emptyMap()
    val copiedVfuMap = HashMap<String, MutableSet<VirtualFileUrl>>()
    addVirtualFileUrlsToMap(copiedVfuMap, property2VfuMap)
    return copiedVfuMap
  }

  private fun addVirtualFileUrlsToMap(result: HashMap<String, MutableSet<VirtualFileUrl>>, value: Any) {
    when (value) {
      is Object2ObjectOpenHashMap<*, *> -> value.forEach { result[it.key as String] = getVirtualFileUrl(it.value) }
      is Pair<*, *> -> result[value.first as String] = getVirtualFileUrl(value.second!!)
    }
  }

  private fun getVirtualFileUrl(value: Any): MutableSet<VirtualFileUrl> {
    return when (value) {
      is ObjectOpenHashSet<*> -> HashSet(value as ObjectOpenHashSet<VirtualFileUrl>)
      else -> mutableSetOf(value as VirtualFileUrl)
    }
  }

  override fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<Pair<WorkspaceEntity, String>> =
    vfu2EntityId[fileUrl]?.asSequence()?.mapNotNull {
      val entityData = entityStorage.entityDataById(it.value) ?: return@mapNotNull null
      entityData.createEntity(entityStorage) to it.key.substring(it.value.asString().length + 1)
    } ?: emptySequence()

  fun getIndexedJarDirectories(): Set<VirtualFileUrl> = entityId2JarDir.values

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  internal fun assertConsistency() {
    val existingVfuInFirstMap = HashSet<VirtualFileUrl>()
    this.entityId2VirtualFileUrl.forEach { (entityId, property2Vfu) ->
      fun assertProperty2Vfu(property: String, vfus: Any) {
        val vfuSet = if (vfus is Set<*>) (vfus as ObjectOpenHashSet<VirtualFileUrl>) else mutableSetOf(vfus as VirtualFileUrl)
        vfuSet.forEach { vfu ->
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
        is Object2ObjectOpenHashMap<*, *> -> property2Vfu.forEach { (property, vfus) -> assertProperty2Vfu(property as String, vfus) }
        is Pair<*, *> -> assertProperty2Vfu(property2Vfu.first as String, property2Vfu.second!!)
      }
    }
    val existingVfuISecondMap = this.vfu2EntityId.keys
    assert(
      existingVfuInFirstMap.size == existingVfuISecondMap.size) { "Different count of VirtualFileUrls EntityId2VirtualFileUrl: ${existingVfuInFirstMap.size} Vfu2EntityId: ${existingVfuISecondMap.size}" }
    existingVfuInFirstMap.removeAll(existingVfuISecondMap)
    assert(existingVfuInFirstMap.isEmpty()) { "Both maps contain the same amount of VirtualFileUrls but they are different" }
  }

  internal fun getCompositeKey(entityId: EntityId, propertyName: String) = "${entityId.asString()}_$propertyName"

  class MutableVirtualFileIndex private constructor(
    // Do not write to [entityId2VirtualFileUrl]  and [vfu2EntityId] directly! Create a dedicated method for that
    // and call [startWrite] before write.
    override var entityId2VirtualFileUrl: EntityId2Vfu,
    override var vfu2EntityId: Vfu2EntityId,
    override var entityId2JarDir: EntityId2JarDir,
  ) : VirtualFileIndex(entityId2VirtualFileUrl, vfu2EntityId, entityId2JarDir), MutableVirtualFileUrlIndex {

    private var freezed = true

    @Synchronized
    override fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
      index((entity as WorkspaceEntityBase).id, propertyName, virtualFileUrl)
    }

    @Synchronized
    internal fun index(id: EntityId, propertyName: String, virtualFileUrls: Set<VirtualFileUrl>) {
      startWrite()
      val newVirtualFileUrls = HashSet(virtualFileUrls)
      fun cleanExistingVfu(existingVfu: Any): Boolean {
        when (existingVfu) {
          is Set<*> -> {
            existingVfu as ObjectOpenHashSet<VirtualFileUrl>
            existingVfu.removeIf { vfu ->
              val elementRemoved = newVirtualFileUrls.remove(vfu)
              if (!elementRemoved) removeFromVfu2EntityIdMap(id, propertyName, vfu)
              return@removeIf !elementRemoved
            }
            if (existingVfu.isEmpty()) return true
          }
          else -> {
            existingVfu as VirtualFileUrl
            val elementRemoved = newVirtualFileUrls.remove(existingVfu)
            if (!elementRemoved) {
              removeFromVfu2EntityIdMap(id, propertyName, existingVfu)
              return true
            }
          }
        }
        return false
      }

      val property2Vfu = entityId2VirtualFileUrl[id]
      if (property2Vfu != null) {
        when (property2Vfu) {
          is Object2ObjectOpenHashMap<*, *> -> {
            val existingVfu = property2Vfu[propertyName]
            if (existingVfu != null && cleanExistingVfu(existingVfu)) {
              property2Vfu.remove(propertyName)
              if (property2Vfu.isEmpty()) entityId2VirtualFileUrl.remove(id)
            }
          }
          is Pair<*, *> -> {
            val existingPropertyName = property2Vfu.first as String
            if (existingPropertyName == propertyName && cleanExistingVfu(property2Vfu.second!!)) entityId2VirtualFileUrl.remove(id)
          }
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
      val removedValue = entityId2VirtualFileUrl.remove(id) ?: return
      when (removedValue) {
        is Object2ObjectOpenHashMap<*, *> -> removedValue.forEach { (property, vfu) ->
          removeFromVfu2EntityIdMap(id, property as String, vfu)
        }
        is Pair<*, *> -> removeFromVfu2EntityIdMap(id, removedValue.first as String, removedValue.second!!)
      }
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      entityId2VirtualFileUrl.clear()
      vfu2EntityId.clear()
      entityId2JarDir.clear()
    }

    @TestOnly
    internal fun copyFrom(another: VirtualFileIndex) {
      startWrite()
      entityId2VirtualFileUrl.putAll(another.entityId2VirtualFileUrl)
      vfu2EntityId.putAll(another.vfu2EntityId)
      entityId2JarDir.putAll(another.entityId2JarDir)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      entityId2VirtualFileUrl = copyEntityMap(entityId2VirtualFileUrl)
      vfu2EntityId = copyVfuMap(vfu2EntityId)
      entityId2JarDir = entityId2JarDir.copy()
    }

    fun toImmutable(): VirtualFileIndex {
      freezed = true
      return VirtualFileIndex(entityId2VirtualFileUrl, vfu2EntityId, entityId2JarDir)
    }

    private fun indexVirtualFileUrl(id: EntityId, propertyName: String, virtualFileUrl: VirtualFileUrl) {
      val property2Vfu = entityId2VirtualFileUrl[id]

      fun addVfuToPropertyName(vfu: Any): Any {
        if (vfu is ObjectOpenHashSet<*>) {
          (vfu as ObjectOpenHashSet<VirtualFileUrl>).add(virtualFileUrl)
          return vfu
        }
        else {
          val result = createSmallMemoryFootprintSet<VirtualFileUrl>()
          result.add(vfu as VirtualFileUrl)
          result.add(virtualFileUrl)
          return result
        }
      }

      if (property2Vfu != null) {
        val newProperty2Vfu = when (property2Vfu) {
          is Object2ObjectOpenHashMap<*, *> -> {
            property2Vfu as Object2ObjectOpenHashMap<String, Any>
            val vfu = property2Vfu[propertyName]
            if (vfu == null) {
              property2Vfu[propertyName] = virtualFileUrl
            }
            else {
              property2Vfu[propertyName] = addVfuToPropertyName(vfu)
            }
            property2Vfu
          }
          is Pair<*, *> -> {
            property2Vfu as Pair<String, Any>
            if (property2Vfu.first != propertyName) {
              val result = createSmallMemoryFootprintMap<String, Any>()
              result[property2Vfu.first] = property2Vfu.second
              result[propertyName] = virtualFileUrl
              result
            }
            else {
              Pair(propertyName, addVfuToPropertyName(property2Vfu.second))
            }
          }
          else -> null
        }
        if (newProperty2Vfu != null) entityId2VirtualFileUrl[id] = newProperty2Vfu
      }
      else {
        entityId2VirtualFileUrl[id] = Pair(propertyName, virtualFileUrl)
      }

      val property2EntityId = vfu2EntityId.getOrDefault(virtualFileUrl, Object2LongOpenHashMap())
      property2EntityId[getCompositeKey(id, propertyName)] = id
      vfu2EntityId[virtualFileUrl] = property2EntityId
    }

    private fun removeByPropertyFromIndexes(id: EntityId, propertyName: String) {
      val property2vfu = entityId2VirtualFileUrl[id] ?: return
      when (property2vfu) {
        is Object2ObjectOpenHashMap<*, *> -> {
          property2vfu as Object2ObjectOpenHashMap<String, Any>
          val vfu = property2vfu.remove(propertyName) ?: return
          if (property2vfu.isEmpty()) entityId2VirtualFileUrl.remove(id)
          removeFromVfu2EntityIdMap(id, propertyName, vfu)
        }
        is Pair<*, *> -> {
          val existingPropertyName = property2vfu.first as String
          if (existingPropertyName != propertyName) return
          entityId2VirtualFileUrl.remove(id)
          removeFromVfu2EntityIdMap(id, propertyName, property2vfu.second!!)
        }
      }
    }

    private fun removeFromVfu2EntityIdMap(id: EntityId, property: String, vfus: Any) {
      when (vfus) {
        is Set<*> -> vfus.forEach { removeFromVfu2EntityIdMap(id, property, it as VirtualFileUrl) }
        else -> removeFromVfu2EntityIdMap(id, property, vfus as VirtualFileUrl)
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

    private fun copyEntityMap(originMap: EntityId2Vfu): EntityId2Vfu {
      val copiedMap = EntityId2Vfu()
      fun getVirtualFileUrl(value: Any) = if (value is Set<*>) ObjectOpenHashSet(value as Set<VirtualFileUrl>) else value

      originMap.forEach { (entityId, vfuMap) ->
        when (vfuMap) {
          is Map<*, *> -> {
            vfuMap as Map<String, *>
            val copiedVfuMap = Object2ObjectOpenHashMap<String, Any>()
            vfuMap.forEach { copiedVfuMap[it.key] = getVirtualFileUrl(it.value!!) }
            copiedMap[entityId] = copiedVfuMap
          }
          is Pair<*, *> -> {
            val copiedVfuPair = Pair(vfuMap.first as String, getVirtualFileUrl(vfuMap.second!!))
            copiedMap[entityId] = copiedVfuPair
          }
        }
      }
      return copiedMap
    }

    private fun copyVfuMap(originMap: Vfu2EntityId): Vfu2EntityId {
      val copiedMap = Vfu2EntityId(getHashingStrategy())
      originMap.forEach { (key, value) -> copiedMap[key] = Object2LongOpenHashMap(value) }
      return copiedMap
    }

    companion object {
      private val LOG = logger<MutableVirtualFileIndex>()
      const val VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY = "entitySource"
      fun from(other: VirtualFileIndex): MutableVirtualFileIndex {
        if (other is MutableVirtualFileIndex) other.freezed = true
        return MutableVirtualFileIndex(other.entityId2VirtualFileUrl, other.vfu2EntityId, other.entityId2JarDir)
      }
    }
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

//---------------------------------------------------------------------
class VirtualFileUrlProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, VirtualFileUrl> {
  @Suppress("UNCHECKED_CAST")
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as VirtualFileUrl
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    (thisRef.diff as WorkspaceEntityStorageBuilderImpl).indexes.virtualFileIndex.index(thisRef.id, property.name, value)
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlNullableProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, VirtualFileUrl?> {
  @Suppress("UNCHECKED_CAST")
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl? {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as VirtualFileUrl?
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    (thisRef.diff as WorkspaceEntityStorageBuilderImpl).indexes.virtualFileIndex.index(thisRef.id, property.name, value)
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlListProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, List<VirtualFileUrl>> {
  @Suppress("UNCHECKED_CAST")
  override fun getValue(thisRef: T, property: KProperty<*>): List<VirtualFileUrl> {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as List<VirtualFileUrl>
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: List<VirtualFileUrl>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    (thisRef.diff as WorkspaceEntityStorageBuilderImpl).indexes.virtualFileIndex.index(thisRef.id, property.name, value.toHashSet())
  }
}

/**
 * This delegate was created specifically for the handling VirtualFileUrls from LibraryRoot
 */
class VirtualFileUrlLibraryRootProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, List<LibraryRoot>> {
  @Suppress("UNCHECKED_CAST")
  override fun getValue(thisRef: T, property: KProperty<*>): List<LibraryRoot> {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as List<LibraryRoot>
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: List<LibraryRoot>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)

    val jarDirectories = mutableSetOf<VirtualFileUrl>()
    (thisRef.diff as WorkspaceEntityStorageBuilderImpl).indexes.virtualFileIndex.index(thisRef.id, property.name, value.map {
      if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
        jarDirectories.add(it.url)
      }
      it.url
    }.toHashSet())
    (thisRef.diff as WorkspaceEntityStorageBuilderImpl).indexes.virtualFileIndex.indexJarDirectories(thisRef.id, jarDirectories)
  }
}