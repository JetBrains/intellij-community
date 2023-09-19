// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.workspace.storage.impl.serialization

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.KryoException
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.esotericsoftware.kryo.kryo5.objenesis.strategy.StdInstantiatorStrategy
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.EntityStorageSnapshotImpl
import com.intellij.platform.workspace.storage.impl.ImmutableEntitiesBarrel
import com.intellij.platform.workspace.storage.impl.KryoInput
import com.intellij.platform.workspace.storage.impl.KryoOutput
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.RefsTable
import com.intellij.platform.workspace.storage.impl.StorageIndexes
import com.intellij.platform.workspace.storage.impl.containers.*
import com.intellij.platform.workspace.storage.impl.indices.*
import com.intellij.platform.workspace.storage.impl.serialization.registration.StorageClassesRegistrar
import com.intellij.platform.workspace.storage.impl.serialization.registration.StorageRegistrar
import com.intellij.platform.workspace.storage.impl.serialization.serializer.*
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.impl.serialization.registration.registerEntitiesClasses
import com.intellij.platform.workspace.storage.metadata.diff.CacheMetadataComparator
import com.intellij.platform.workspace.storage.metadata.diff.ComparisonResult
import com.intellij.platform.workspace.storage.metadata.diff.MetadataComparator
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.*
import org.jetbrains.annotations.TestOnly
import java.lang.UnsupportedOperationException
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.system.measureNanoTime

private val LOG = logger<EntityStorageSerializerImpl>()

public class EntityStorageSerializerImpl(
  internal val typesResolver: EntityTypesResolver,
  private val virtualFileManager: VirtualFileUrlManager,
  private val urlRelativizer: UrlRelativizer? = null
) : EntityStorageSerializer {
  public companion object {
    public const val STORAGE_SERIALIZATION_VERSION: String = "v1"
  }

  private val interner: StorageInterner = StorageInternerImpl()


  @set:TestOnly
  override var serializerDataFormatVersion: String = STORAGE_SERIALIZATION_VERSION

  internal fun createKryo(): Pair<Kryo, Object2IntMap<TypeInfo>> {
    val kryo = Kryo()

    kryo.setAutoReset(false)
    kryo.references = true
    kryo.instantiatorStrategy = StdInstantiatorStrategy()

    val classCache = Object2IntOpenHashMap<TypeInfo>()
    val registrar: StorageRegistrar = StorageClassesRegistrar(
      StorageSerializerUtil(
        typesResolver,
        virtualFileManager,
        interner,
        urlRelativizer,
        classCache
      )
    )

    registrar.registerClasses(kryo)

    return kryo to classCache
  }

  override fun serializeCache(file: Path, storage: EntityStorageSnapshot): SerializationResult {
    storage as EntityStorageSnapshotImpl

    val output = createKryoOutput(file)
    return try {
      val (kryo, classCache) = createKryo()

      // Save version
      output.writeString(serializerDataFormatVersion)

      val entitiesMetadata = getCacheMetadata(storage, typesResolver)
      kryo.writeObject(output, entitiesMetadata)// Serialize all Entities, Entity Source and Symbolic id metadata from the storage

      writeAndRegisterClasses(kryo, output, storage, entitiesMetadata, classCache) // Register entities classes

      // Write entity data and references
      kryo.writeClassAndObject(output, storage.entitiesByType)
      kryo.writeObject(output, storage.refs)

      // Write indexes
      kryo.writeObject(output, storage.indexes.softLinks)

      kryo.writeObject(output, storage.indexes.virtualFileIndex.entityId2VirtualFileUrl)
      kryo.writeObject(output, storage.indexes.virtualFileIndex.vfu2EntityId)
      kryo.writeObject(output, storage.indexes.virtualFileIndex.entityId2JarDir)

      kryo.writeObject(output, storage.indexes.entitySourceIndex)
      kryo.writeObject(output, storage.indexes.symbolicIdIndex)

      SerializationResult.Success(output.total())
    }
    catch (e: Exception) {
      output.reset()
      LOG.warn("Exception at project serialization", e)
      SerializationResult.Fail(e.message)
    }
    finally {
      closeOutput(output)
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun deserializeCache(file: Path): Result<MutableEntityStorage?> {
    LOG.debug("Start deserializing workspace model cache")
    val deserializedCache = createKryoInput(file).use { input ->
      val (kryo, classCache) = createKryo()

      try { // Read version
        val cacheVersion = input.readString()
        if (cacheVersion != serializerDataFormatVersion) {
          LOG.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
          return Result.success(null)
        }

        var time = System.nanoTime()

        val cacheMetadata = kryo.readObject(input, CacheMetadata::class.java)
        val currentMetadata = loadCurrentEntitiesMetadata(cacheMetadata, typesResolver)
        val comparisonResult = compareMetadata(cacheMetadata, currentMetadata)
        if (!comparisonResult.areEquals) {
          LOG.info("Cache isn't loaded. Reason:\n${comparisonResult.info}")
          return Result.failure(UnsupportedEntitiesVersionException())
        }

        time = logAndResetTime(time) { measuredTime -> "Read cache metadata and compare it with the existing metadata: $measuredTime ns" }


        readAndRegisterClasses(kryo, input, cacheMetadata, classCache)
        time = logAndResetTime(time) { measuredTime -> "Read and register classes: $measuredTime ns" }


        // Read entity data and references
        val entitiesBarrel = kryo.readClassAndObject(input) as ImmutableEntitiesBarrel
        val refsTable = kryo.readObject(input, RefsTable::class.java)

        time = logAndResetTime(time) { measuredTime -> "Read data and references: $measuredTime ns" }

        // Read indexes
        val softLinks = kryo.readObject(input, MultimapStorageIndex::class.java)

        time = logAndResetTime(time) { measuredTime -> "Read soft links: $measuredTime ns" }

        val entityId2VirtualFileUrlInfo = kryo.readObject(input, Long2ObjectOpenHashMap::class.java) as Long2ObjectOpenHashMap<Any>
        val vfu2VirtualFileUrlInfo = kryo.readObject(input,
                                                     Object2ObjectOpenCustomHashMap::class.java) as Object2ObjectOpenCustomHashMap<VirtualFileUrl, Object2LongMap<EntityIdWithProperty>>
        val entityId2JarDir = kryo.readObject(input, BidirectionalLongMultiMap::class.java) as BidirectionalLongMultiMap<VirtualFileUrl>

        val virtualFileIndex = VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo, entityId2JarDir)

        time = logAndResetTime(time) { measuredTime -> "Read virtual file index: $measuredTime ns" }

        val entitySourceIndex = kryo.readObject(input, EntityStorageInternalIndex::class.java) as EntityStorageInternalIndex<EntitySource>

        time = logAndResetTime(time) { measuredTime -> "Read entity source index: $measuredTime ns" }

        val symbolicIdIndex = kryo.readObject(input, SymbolicIdInternalIndex::class.java)

        time = logAndResetTime(time) { measuredTime -> "Persistent id index: $measuredTime ns" }

        val storageIndexes = StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, symbolicIdIndex)

        val storage = EntityStorageSnapshotImpl(entitiesBarrel, refsTable, storageIndexes)
        val builder = MutableEntityStorageImpl(storage)

        builder.entitiesByType.entityFamilies.forEach { family ->
          family?.entities?.asSequence()?.filterNotNull()?.forEach { entityData -> builder.createAddEvent(entityData) }
        }

        if (LOG.isTraceEnabled) {
          builder.assertConsistency()
          LOG.trace("Builder loaded from caches has no consistency issues")
        }

        builder
      }
      catch (e: Exception) {
        return Result.failure(e)
      }
    }
    return Result.success(deserializedCache)
  }


  private fun compareMetadata(cacheMetadata: CacheMetadata, currentMetadata: List<StorageTypeMetadata>?): ComparisonResult {
    if (currentMetadata == null) {
      return object : ComparisonResult {
        override val areEquals: Boolean = false
        override val info: String = "Failed to load existing metadata"
      }
    }
    return CacheMetadataComparator().areEquals(cacheMetadata.toList(), currentMetadata)
  }


  private fun writeAndRegisterClasses(kryo: Kryo, output: Output, entityStorage: EntityStorageSnapshotImpl,
                                      cacheMetadata: CacheMetadata, classCache: Object2IntMap<TypeInfo>) {
    registerEntitiesClasses(kryo, cacheMetadata, typesResolver, classCache)

    val vfsClasses = hashSetOf<Class<*>>()
    entityStorage.indexes.virtualFileIndex.vfu2EntityId.keys.forEach(Consumer { virtualFileUrl ->
      vfsClasses.add(virtualFileUrl::class.java)
    })

    output.writeVarInt(vfsClasses.size, true)
    vfsClasses.forEach { clazz ->
      kryo.register(clazz)
      kryo.writeClassAndObject(output, clazz.typeInfo)
    }
  }

  private fun readAndRegisterClasses(kryo: Kryo, input: Input, cacheMetadata: CacheMetadata,
                                     classCache: Object2IntMap<TypeInfo>) {
    registerEntitiesClasses(kryo, cacheMetadata, typesResolver, classCache)

    val nonObjectCount = input.readVarInt(true)
    repeat(nonObjectCount) {
      val objectClass = kryo.readClassAndObject(input) as TypeInfo
      val resolvedClass = typesResolver.resolveClass(objectClass.fqName, objectClass.pluginId)
      classCache.putIfAbsent(objectClass, resolvedClass.toClassId())
      kryo.register(resolvedClass)
    }
  }

  internal val Class<*>.typeInfo: TypeInfo
    get() = getTypeInfo(this, interner, typesResolver)

  @TestOnly
  @Suppress("UNCHECKED_CAST")
  public fun deserializeCacheAndDiffLog(file: Path, diffLogFile: Path): MutableEntityStorage? {
    val builder = deserializeCache(file).getOrThrow() ?: return null

    var log: ChangeLog
    createKryoInput(diffLogFile).use { input ->
      val (kryo, classCache) = createKryo()

      // Read version
      val cacheVersion = input.readString()
      if (cacheVersion != serializerDataFormatVersion) {
        LOG.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
        return null
      }

      val cacheMetadata = kryo.readObject(input, CacheMetadata::class.java)
      val currentMetadata = loadCurrentEntitiesMetadata(cacheMetadata, typesResolver)
      val comparisonResult = compareMetadata(cacheMetadata, currentMetadata)
      if (!comparisonResult.areEquals) {
        LOG.info("Cache isn't loaded. Reason:\n${comparisonResult.info}")
        return null
      }

      readAndRegisterClasses(kryo, input, cacheMetadata, classCache)

      log = kryo.readClassAndObject(input) as ChangeLog
    }

    builder as MutableEntityStorageImpl
    builder.changeLog.changeLog.clear()
    builder.changeLog.changeLog.putAll(log)

    return builder
  }

  @TestOnly
  @Suppress("UNCHECKED_CAST")
  public fun deserializeClassToIntConverter(file: Path) {
    createKryoInput(file).use { input ->
      val (kryo, _) = createKryo()

      // Read version
      val cacheVersion = input.readString()
      if (cacheVersion != serializerDataFormatVersion) {
        LOG.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
        return
      }

      val classes = kryo.readClassAndObject(input) as List<Pair<TypeInfo, Int>>
      val map = Object2IntOpenHashMap<Class<*>>()
      for ((first, second) in classes) {
        map.put(typesResolver.resolveClass(first.fqName, first.pluginId), second)
      }
      ClassToIntConverter.getInstance().fromMap(map)
    }
  }

  internal fun createKryoOutput(file: Path): Output {
    val output = KryoOutput(file)
    output.variableLengthEncoding = false
    return output
  }

  internal fun createKryoInput(file: Path): Input {
    val input = KryoInput(file)
    input.variableLengthEncoding = false
    return input
  }


  internal fun closeOutput(output: Output) {
    try {
      output.close()
    }
    catch (e: KryoException) {
      LOG.warn("Exception at project serialization", e)
      SerializationResult.Fail(e.message)
    }
  }

  private fun logAndResetTime(time: Long, measuredTimeToText: (Long) -> String): Long {
    LOG.debug(measuredTimeToText.invoke(System.nanoTime() - time))
    return System.nanoTime()
  }
}

public class UnsupportedEntitiesVersionException: Exception("Version of the entities in cache is not supported")
