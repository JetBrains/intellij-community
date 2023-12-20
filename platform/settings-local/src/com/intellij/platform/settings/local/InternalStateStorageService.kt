// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.platform.settings.local

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.settings.RawSettingSerializerDescriptor
import com.intellij.platform.settings.SettingSerializerDescriptor
import com.intellij.platform.settings.SettingValueSerializer
import io.opentelemetry.api.metrics.Meter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

internal val cborFormat: Cbor = Cbor {
   ignoreUnknownKeys = true
 }

/**
 * CBOR is used instead of ION - no mature and robust implementation for kotlinx-serialization.
 */
internal class InternalStateStorageService(@JvmField val map: MvMapManager, telemetryScopeName: String) {
  private val meter: Meter = TelemetryManager.getMeter(Scope(telemetryScopeName, PlatformMetrics))

  private val getMeasurer = Measurer(meter, "get")
  private val setMeasurer = Measurer(meter, "set")

  fun clear() {
    map.clear()
  }

  fun <T : Any> getValue(key: String, serializer: SettingSerializerDescriptor<T>, pluginId: PluginId): T? {
    val start = System.nanoTime()
    var bytes: ByteArray? = null
    try {
      bytes = map.get(key)
      @Suppress("UNCHECKED_CAST")
      val result: T? = bytes?.let {
        if (serializer === RawSettingSerializerDescriptor) {
          bytes as T
        }
        else {
          cborFormat.decodeFromByteArray((serializer as SettingValueSerializer<T>).serializer, bytes)
        }
      }
      getMeasurer.add(System.nanoTime() - start)
      return result
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
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
  }

  fun <T : Any> setValue(key: String, value: T?, serializer: SettingSerializerDescriptor<T>, pluginId: PluginId) {
    val start = System.nanoTime()
    try {
      if (value == null) {
        map.remove(key)
      }
      else if (serializer === RawSettingSerializerDescriptor) {
        map.put(key, value as ByteArray)
      }
      else {
        @Suppress("UNCHECKED_CAST")
        map.put(key, cborFormat.encodeToByteArray((serializer as SettingValueSerializer<T>).serializer, value))
      }
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

  fun <T : Any> putIfDiffers(key: String, value: T?, serializer: SettingSerializerDescriptor<T>, pluginId: PluginId) {
    val start = System.nanoTime()
    try {
      if (value == null) {
        map.remove(key)
      }
      else if (serializer === RawSettingSerializerDescriptor) {
        map.putIfDiffers(key, value as ByteArray)
      }
      else {
        @Suppress("UNCHECKED_CAST")
        map.putIfDiffers(key, cborFormat.encodeToByteArray((serializer as SettingValueSerializer<T>).serializer, value))
      }
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