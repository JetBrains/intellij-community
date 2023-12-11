// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MismatchedLightServiceLevelAndCtor")

package com.intellij.platform.settings.local

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.diagnostic.PluginException
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.settings.SettingValueSerializer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.io.*
import io.opentelemetry.api.metrics.Meter
import kotlinx.serialization.SerializationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder

private val cacheScope = Scope("cacheStateStorage", PlatformMetrics)

// todo get rid of Disposable as soon as it will be possible to get service from coroutine scope
@Suppress("NonDefaultConstructor")
@ApiStatus.Internal
@Service(Service.Level.APP, Service.Level.PROJECT)
internal class CacheStatePropertyService(componentManager: ComponentManager) : Disposable, SettingsSavingComponent {
  private val map = createOrResetPersistentMap(getStorageDir(componentManager))
  private val isChanged = AtomicBoolean(false)

  private val meter: Meter = TelemetryManager.getMeter(cacheScope)

  private val getMeasurer = Measurer(meter, "get")
  private val setMeasurer = Measurer(meter, "set")

  override fun dispose() {
    map.close()
  }

  @TestOnly
  fun clear() {
    map.processKeys {
      map.remove(it)
      true
    }
  }

  @TestOnly
  fun getCacheStorageAsMap(): Map<String, String> {
    val result = HashMap<String, String>()
    map.processKeys { key ->
      map.get(key)?.let {
        result.put(key, it.decodeToString())
      }
      true
    }
    return result
  }

  override suspend fun save() {
    if (isChanged.compareAndSet(true, false)) {
      map.force()
    }
  }

  fun <T : Any> getValue(key: String, serializer: SettingValueSerializer<T>, pluginId: PluginId): T? {
    val start = System.nanoTime()
    var bytes: ByteArray? = null
    try {
      bytes = map.get(key)
      val result = bytes?.let { serializer.decode(it) }
      getMeasurer.add(System.nanoTime() - start)
      return result
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: SerializationException) {
      var message = "Cannot deserialize value for key $key (size=${bytes?.size ?: "null"}"
      try {
        map.remove(key)
        if (bytes == null || bytes.isEmpty()) {
          message += ")"
        }
        else {
          val keyForInvestigation = "${key}.__corrupted__"
          map.put(keyForInvestigation, bytes)
          message += ", value will be stored under key ${keyForInvestigation})"
        }
      }
      catch (e: Throwable) {
        e.addSuppressed(e)
      }

      thisLogger().error(PluginException(message, e, pluginId))
      return null
    }
    catch (e: Throwable) {
      thisLogger().error(PluginException("Cannot deserialize value for key $key", e, pluginId))
      return null
    }
  }

  fun <T : Any> setValue(key: String, value: T?, serializer: SettingValueSerializer<T>, pluginId: PluginId) {
    val start = System.nanoTime()
    try {
      if (value == null) {
        map.remove(key)
      }
      else {
        map.put(key, serializer.encode(value))
      }
      isChanged.set(true)
      setMeasurer.add(System.nanoTime() - start)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      thisLogger().error(PluginException(e, pluginId))
    }
  }
}

private fun getStorageDir(componentManager: ComponentManager): Path {
  val oldCacheFile = componentManager.service<IComponentStore>().storageManager.expandMacro(StoragePathMacros.CACHE_FILE)
  return oldCacheFile.parent.resolve(oldCacheFile.fileName.toString().removeSuffix(FileStorageCoreUtil.DEFAULT_EXT))
}

private class CacheStateStorageInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    val dir = getStorageDir(ApplicationManager.getApplication())
    if (Files.isDirectory(dir)) {
      Files.write(dir.resolve(".invalidated"), ArrayUtilRt.EMPTY_BYTE_ARRAY, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
  }
}

private fun createOrResetPersistentMap(dbDir: Path): PersistentMapImpl<String, ByteArray> {
  val markerFile = dbDir.resolve(".invalidated")
  if (Files.exists(markerFile)) {
    NioFiles.deleteRecursively(dbDir)
  }

  val dbFile = dbDir.resolve("${dbDir.fileName}.db")
  try {
    return createPersistentMap(dbFile)
  }
  catch (e: CorruptedException) {
    logger<CacheStatePropertyService>().warn("Cache state storage is corrupted (${e.message})")
  }
  catch (e: Throwable) {
    logger<CacheStatePropertyService>().warn("Cannot open cache state storage, will be recreated", e)
  }

  NioFiles.deleteRecursively(dbDir)
  return createPersistentMap(dbFile)
}

private fun createPersistentMap(dbFile: Path): PersistentMapImpl<String, ByteArray> {
  val builder = PersistentMapBuilder.newBuilder(dbFile, object : KeyDescriptor<String> {
    override fun getHashCode(value: String) = Hashing.komihash5_0().hashCharsToInt(value)

    override fun save(out: DataOutput, value: String) {
      IOUtil.writeUTF(out, value)
    }

    override fun read(input: DataInput): String {
      return IOUtil.readUTF(input)
    }

    override fun isEqual(val1: String, val2: String) = val1 == val2
  }, object : DataExternalizer<ByteArray> {
    override fun save(out: DataOutput, value: ByteArray) {
      out.writeInt(value.size)
      out.write(value)
    }

    override fun read(input: DataInput): ByteArray {
      val result = ByteArray(input.readInt())
      input.readFully(result)
      return result
    }
  })
    .withStorageLockContext(StorageLockContext(/* useReadWriteLock = */ true, /* cacheChannels = */ true, /* disableAssertions = */ true))
    .withVersion(2)

  return PersistentMapImpl(builder, PersistentHashMapValueStorage.CreationTimeOptions(/* readOnly = */ false,
                                                                                      /* compactChunksWithValueDeserialization = */ false,
                                                                                      /* hasNoChunks = */ false,
                                                                                      /* doCompression = */ false))
}

private class Measurer(meter: Meter, subKey: String) {
  private val time = LongAdder()
  private val counter = LongAdder()

  private val timeObserver = meter.counterBuilder("cacheStateStorage.$subKey.duration").buildObserver()
  private val counterObserver = meter.counterBuilder("cacheStateStorage.$subKey.counter").buildObserver()

  init {
    meter.batchCallback(
      {
        // compute in nanoseconds to avoid round errors, but report as ms
        timeObserver.record(TimeUnit.NANOSECONDS.toMillis(time.sum()))
        counterObserver.record(counter.sum())
      },
      counterObserver, timeObserver
    )
  }

  fun add(nano: Long) {
    time.add(nano)
    counter.increment()
  }
}