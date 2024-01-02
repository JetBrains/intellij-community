// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.workspace.storage.impl.serialization

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.KryoException
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.esotericsoftware.kryo.kryo5.objenesis.strategy.StdInstantiatorStrategy
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.JPS
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMillis
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap
import com.intellij.platform.workspace.storage.impl.containers.Object2IntWithDefaultMap
import com.intellij.platform.workspace.storage.impl.containers.Object2LongWithDefaultMap
import com.intellij.platform.workspace.storage.impl.indices.*
import com.intellij.platform.workspace.storage.impl.serialization.registration.StorageClassesRegistrar
import com.intellij.platform.workspace.storage.impl.serialization.registration.StorageRegistrar
import com.intellij.platform.workspace.storage.impl.serialization.registration.registerEntitiesClasses
import com.intellij.platform.workspace.storage.impl.serialization.serializer.StorageSerializerUtil
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import io.opentelemetry.api.metrics.Meter
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

private val LOG = logger<EntityStorageSerializerImpl>()

public class EntityStorageSerializerImpl(
  private val typesResolver: EntityTypesResolver,
  private val virtualFileManager: VirtualFileUrlManager,
  private val urlRelativizer: UrlRelativizer? = null
) : EntityStorageSerializer {
  public companion object {
    public const val STORAGE_SERIALIZATION_VERSION: String = "version5"

    private val loadCacheMetadataFromFileTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadCacheMetadataFromFileTimeCounter = meter.counterBuilder("workspaceModel.load.cache.metadata.from.file.ms").buildObserver()

      meter.batchCallback(
        { loadCacheMetadataFromFileTimeCounter.record(loadCacheMetadataFromFileTimeMs.get()) },
        loadCacheMetadataFromFileTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(JPS))
    }
  }

  private val interner: StorageInterner = StorageInternerImpl()


  @set:TestOnly
  override var serializerDataFormatVersion: String = STORAGE_SERIALIZATION_VERSION

  internal fun createKryo(): Pair<Kryo, Object2IntWithDefaultMap<TypeInfo>> {
    val kryo = Kryo()

    kryo.setAutoReset(false)
    kryo.references = true
    kryo.instantiatorStrategy = StdInstantiatorStrategy()

    val classCache = Object2IntWithDefaultMap<TypeInfo>()
    val registrar: StorageRegistrar = StorageClassesRegistrar(
      StorageSerializerUtil(
        typesResolver,
        virtualFileManager,
        interner,
        urlRelativizer,
        classCache
      ),
      typesResolver
    )

    registrar.registerClasses(kryo)

    return kryo to classCache
  }

  override fun serializeCache(file: Path, storage: EntityStorageSnapshot): SerializationResult {
    storage as ImmutableEntityStorageImpl

    val output = createKryoOutput(file)
    return try {
      val (kryo, classCache) = createKryo()

      // Save version
      output.writeString(serializerDataFormatVersion)

      val cacheMetadata = getCacheMetadata(storage, typesResolver)

      kryo.writeObject(output, cacheMetadata)// Serialize all Entities, Entity Source and Symbolic id metadata from the storage

      writeAndRegisterClasses(kryo, output, storage, cacheMetadata, classCache) // Register entities classes

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
      SerializationResult.Fail(e)
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
        val metadataDeserializationStartTimeMs = System.currentTimeMillis()

        val cacheMetadata = kryo.readObject(input, CacheMetadata::class.java)
        val comparisonResult = compareWithCurrentEntitiesMetadata(cacheMetadata, typesResolver)
        if (!comparisonResult.areEquals) {
          LOG.info("Cache isn't loaded. Reason:\n${comparisonResult.info}")
          return Result.failure(UnsupportedEntitiesVersionException())
        }

        loadCacheMetadataFromFileTimeMs.addElapsedTimeMillis(metadataDeserializationStartTimeMs)

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
                                                     Object2ObjectOpenCustomHashMap::class.java) as Object2ObjectOpenCustomHashMap<VirtualFileUrl, Object2LongWithDefaultMap<EntityIdWithProperty>>
        val entityId2JarDir = kryo.readObject(input, BidirectionalLongMultiMap::class.java) as BidirectionalLongMultiMap<VirtualFileUrl>

        val virtualFileIndex = VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo, entityId2JarDir)

        time = logAndResetTime(time) { measuredTime -> "Read virtual file index: $measuredTime ns" }

        val entitySourceIndex = kryo.readObject(input, EntityStorageInternalIndex::class.java) as EntityStorageInternalIndex<EntitySource>

        time = logAndResetTime(time) { measuredTime -> "Read entity source index: $measuredTime ns" }

        val symbolicIdIndex = kryo.readObject(input, SymbolicIdInternalIndex::class.java)

        time = logAndResetTime(time) { measuredTime -> "Persistent id index: $measuredTime ns" }

        val storageIndexes = StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, symbolicIdIndex)

        val storage = ImmutableEntityStorageImpl(entitiesBarrel, refsTable, storageIndexes)
        val builder = MutableEntityStorageImpl(storage)

        builder.entitiesByType.entityFamilies.forEach { family ->
          family?.entities?.asSequence()?.filterNotNull()?.forEach { entityData ->
            builder.changeLog.addAddEvent(entityData.createEntityId(), entityData)
          }
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


  private fun writeAndRegisterClasses(kryo: Kryo, output: Output, entityStorage: ImmutableEntityStorageImpl,
                                      cacheMetadata: CacheMetadata, classCache: Object2IntWithDefaultMap<TypeInfo>) {
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
                                     classCache: Object2IntWithDefaultMap<TypeInfo>) {
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


  private fun createKryoOutput(file: Path): Output {
    val output = KryoOutput(file)
    output.variableLengthEncoding = false
    return output
  }

  private fun createKryoInput(file: Path): Input {
    val input = KryoInput(file)
    input.variableLengthEncoding = false
    return input
  }


  private fun closeOutput(output: Output) {
    try {
      output.close()
    }
    catch (e: KryoException) {
      LOG.warn("Exception at project serialization", e)
    }
  }

  private fun logAndResetTime(time: Long, measuredTimeToText: (Long) -> String): Long {
    LOG.debug(measuredTimeToText.invoke(System.nanoTime() - time))
    return System.nanoTime()
  }
}

public class UnsupportedEntitiesVersionException: Exception("Version of the entities in cache is not supported")
