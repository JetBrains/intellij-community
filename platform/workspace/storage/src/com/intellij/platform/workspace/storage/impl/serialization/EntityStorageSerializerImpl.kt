// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
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
import com.intellij.platform.workspace.storage.metadata.diff.ComparisonResult
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import io.opentelemetry.api.metrics.Meter
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import kotlinx.collections.immutable.PersistentMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

private val LOG = logger<EntityStorageSerializerImpl>()

@ApiStatus.Internal
public class EntityStorageSerializerImpl(
  private val typesResolver: EntityTypesResolver,
  private val virtualFileManager: VirtualFileUrlManager,
  private val urlRelativizer: UrlRelativizer? = null,
  private val ijBuildVersion: String,
) : EntityStorageSerializer {
  public companion object {
    public const val STORAGE_SERIALIZATION_VERSION: String = "version13"

    private val loadCacheMetadataFromFileTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadCacheMetadataFromFileTimeCounter = meter.counterBuilder("workspaceModel.load.cache.metadata.from.file.ms").buildObserver()

      meter.batchCallback(
        { loadCacheMetadataFromFileTimeCounter.record(loadCacheMetadataFromFileTimeMs.asMilliseconds()) },
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

  internal fun createKryo(): Triple<Kryo, Object2IntWithDefaultMap<TypeInfo>, StorageSerializerUtil> {
    val kryo = Kryo()

    kryo.setAutoReset(false)
    kryo.references = true
    kryo.instantiatorStrategy = StdInstantiatorStrategy()

    val classCache = Object2IntWithDefaultMap<TypeInfo>()
    val storageSerializerUtil = StorageSerializerUtil(
      typesResolver,
      virtualFileManager,
      interner,
      urlRelativizer,
      classCache
    )
    val registrar: StorageRegistrar = StorageClassesRegistrar(storageSerializerUtil, typesResolver)

    registrar.registerClasses(kryo)

    return Triple(kryo, classCache, storageSerializerUtil)
  }

  private fun writeIndexes(kryo: Kryo, output: Output, indexes: ImmutableStorageIndexes, storageSerializerUtil: StorageSerializerUtil) {
    kryo.writeObject(output, indexes.softLinks)

    kryo.writeObject(output, indexes.virtualFileIndex.entityId2VirtualFileUrl, storageSerializerUtil.getEntityId2VfuPersistentMapSerializer())
    kryo.writeObject(output, indexes.virtualFileIndex.vfu2EntityId)
    kryo.writeObject(output, indexes.virtualFileIndex.entityId2JarDir)

    kryo.writeObject(output, indexes.entitySourceIndex)
    kryo.writeObject(output, indexes.symbolicIdIndex)
  }

  override fun serializeCache(file: Path, storage: ImmutableEntityStorage): SerializationResult {
    storage as ImmutableEntityStorageImpl

    val output = createKryoOutput(file)
    return try {
      val (kryo, classCache, storageSerializerUtil) = createKryo()

      // Save versions
      output.writeString(serializerDataFormatVersion)
      output.writeString(ijBuildVersion)

      val cacheMetadata = getCacheMetadata(storage, typesResolver)

      kryo.writeObject(output, cacheMetadata) // Serialize all Entities, Entity Source and Symbolic id metadata from the storage

      registerEntitiesClasses(kryo, cacheMetadata, typesResolver, classCache)

      // Write entity data and references
      kryo.writeClassAndObject(output, storage.entitiesByType)
      kryo.writeObject(output, storage.refs)

      // Write indexes
      writeIndexes(kryo, output, storage.indexes, storageSerializerUtil)

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
      val (kryo, classCache, storageSerializerUtil) = createKryo()

      try { // Read version
        if (!checkCacheVersionIdentical(input)) {
          return Result.success(null)
        }
        val cacheIjBuildVersion = input.readString()

        var time = System.nanoTime()
        val metadataDeserializationStartTimeMs = Milliseconds.now()

        val (comparisonResult, cacheMetadata) = compareCacheMetadata(kryo, input)
        if (!comparisonResult.areEquals) {
          LOG.info("Cache isn't loaded. Reason:\n${comparisonResult.info}")
          return Result.success(null)
        }

        loadCacheMetadataFromFileTimeMs.addElapsedTime(metadataDeserializationStartTimeMs)

        time = logAndResetTime(time) { measuredTime -> "Read cache metadata and compare it with the existing metadata: $measuredTime ns" }


        registerEntitiesClasses(kryo, cacheMetadata, typesResolver, classCache)
        time = logAndResetTime(time) { measuredTime -> "Read and register classes: $measuredTime ns" }


        // Read entity data and references
        val entitiesBarrel = kryo.readClassAndObject(input) as ImmutableEntitiesBarrel
        val refsTable = kryo.readObject(input, RefsTable::class.java)

        time = logAndResetTime(time) { measuredTime -> "Read data and references: $measuredTime ns" }

        // Read indexes
        val softLinks = kryo.readObject(input, ImmutableMultimapStorageIndex::class.java)

        time = logAndResetTime(time) { measuredTime -> "Read soft links: $measuredTime ns" }
        
        val entityId2VirtualFileUrlInfo = kryo.readObject(input, PersistentMap::class.java, storageSerializerUtil.getEntityId2VfuPersistentMapSerializer()) as PersistentMap<Long, Any>
        val vfu2VirtualFileUrlInfo = kryo.readObject(input,
                                                     Object2ObjectOpenCustomHashMap::class.java) as Object2ObjectOpenCustomHashMap<VirtualFileUrl, Object2LongWithDefaultMap<EntityIdWithProperty>>
        val entityId2JarDir = kryo.readObject(input, BidirectionalLongMultiMap::class.java) as BidirectionalLongMultiMap<VirtualFileUrl>

        val virtualFileIndex = VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo, entityId2JarDir)

        time = logAndResetTime(time) { measuredTime -> "Read virtual file index: $measuredTime ns" }

        val entitySourceIndex = kryo.readObject(input, EntityStorageInternalIndex::class.java) as EntityStorageInternalIndex<EntitySource>

        time = logAndResetTime(time) { measuredTime -> "Read entity source index: $measuredTime ns" }

        val symbolicIdIndex = kryo.readObject(input, SymbolicIdInternalIndex::class.java)

        time = logAndResetTime(time) { measuredTime -> "Persistent id index: $measuredTime ns" }

        val storageIndexes = ImmutableStorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, symbolicIdIndex)

        val storage = ImmutableEntityStorageImpl(entitiesBarrel, refsTable, storageIndexes)
        val builder = MutableEntityStorageImpl(storage)

        builder.entitiesByType.entityFamilies.forEach { family ->
          family?.entities?.asSequence()?.filterNotNull()?.forEach { entityData ->
            builder.changeLog.addAddEvent(entityData.createEntityId(), entityData)
          }
        }

        if (LOG.isTraceEnabled || cacheIjBuildVersion != ijBuildVersion) {
          try {
            builder.assertConsistency()
          }
          catch (e: Throwable) {
            return Result.failure(e)
          }
          LOG.info("Builder loaded from caches has no consistency issues. Current version of IJ: $ijBuildVersion. IJ version from cache: $cacheIjBuildVersion")
        }

        builder
      }
      catch (e: Exception) {
        return Result.failure(e)
      }
    }
    return Result.success(deserializedCache)
  }

  private fun checkCacheVersionIdentical(input: Input): Boolean {
    val cacheVersion = input.readString()
    if (cacheVersion != serializerDataFormatVersion) {
      LOG.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
      return false
    }
    return true
  }

  private fun compareCacheMetadata(kryo: Kryo, input: Input): Pair<ComparisonResult, CacheMetadata> {
    val cacheMetadata = kryo.readObject(input, CacheMetadata::class.java)
    return compareWithCurrentEntitiesMetadata(cacheMetadata, typesResolver) to cacheMetadata
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

  @TestOnly
  public fun calculateCacheDiff(file: Path): String {
    createKryoInput(file).use { input ->
      val (kryo, _) = createKryo()

      checkCacheVersionIdentical(input)
      input.readString() // Just reading the version of IJ build

      val (comparisonResult, _) = compareCacheMetadata(kryo, input)
      return comparisonResult.info
    }
  }
}

internal class UnsupportedEntitiesVersionException : Exception("Version of the entities in cache is not supported")
