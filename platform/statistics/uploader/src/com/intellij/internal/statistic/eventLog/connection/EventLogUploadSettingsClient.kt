// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection

import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.configuration.RegionCode
import com.jetbrains.fus.reporting.jvm.JvmHttpClient
import com.jetbrains.fus.reporting.jvm.ProxyInfo
import com.jetbrains.fus.reporting.model.serialization.SerializationException
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.concurrent.TimeUnit

/**
 * The Client periodically downloads configuration.
 * The frequency can be set, it is 10 minutes by default.
 * Use China configuration url for the China region.
 * Use the test configuration url if applicationInfo.isTestConfig is true.
 * */
@ApiStatus.Internal
open class EventLogUploadSettingsClient(
  override val recorderId: String,
  override val applicationInfo: EventLogApplicationInfo,
  cacheTimeoutMs: Long = TimeUnit.MINUTES.toMillis(10)
) : EventLogSettingsClient() {
  companion object {
    const val chinaRegion: String = "china" //com.intellij.ide.Region.CHINA
  }
  override var configurationClient: ConfigurationClient = ConfigurationClientFactory.create(
    recorderId = recorderId,
    productCode = applicationInfo.productCode,
    productVersion = applicationInfo.productVersion,
    isTestConfiguration = applicationInfo.isTestConfig,
    httpClient = JvmHttpClient(
      sslContextProvider = { applicationInfo.connectionSettings.provideSSLContext() },
      proxyProvider = { configurationUrl ->
        ProxyInfo(applicationInfo.connectionSettings.provideProxy(configurationUrl).proxy)
      },
      extraHeadersProvider = { applicationInfo.connectionSettings.provideExtraHeaders() },
      userAgent = applicationInfo.connectionSettings.provideUserAgent()
    ),
    regionCode = if (applicationInfo.regionalCode == chinaRegion) RegionCode.CN else RegionCode.ALL,
    serializer = FusJacksonSerializer(),
    cacheTimeoutMs = cacheTimeoutMs
  )

  class FusJacksonSerializer: FusJsonSerializer {
    private val SERIALIZATION_MAPPER: JsonMapper by lazy {
      JsonMapper
        .builder()
        .addModule(kotlinModule())
        .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        .defaultPrettyPrinter(CustomPrettyPrinter())
        .build()
    }

    private val DESERIALIZATION_MAPPER: JsonMapper by lazy {
      JsonMapper
        .builder()
        .addModule(kotlinModule())
        .enable(DeserializationFeature.USE_LONG_FOR_INTS)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()
    }

    override fun toJson(data: Any): String = try {
      SERIALIZATION_MAPPER
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(data)
    } catch (e: Exception) {
      throw SerializationException(e)
    }

    override fun <T> fromJson(json: String, clazz: Class<T>): T = try {
      DESERIALIZATION_MAPPER
        .readValue(json, clazz)
    } catch (e: Exception) {
      throw SerializationException(e)
    }
  }

  private class CustomPrettyPrinter : DefaultPrettyPrinter {
    init {
      _objectIndenter = DefaultIndenter("  ", "\n")
      _arrayIndenter = DefaultIndenter("  ", "\n")
    }

    constructor() : super()
    constructor(base: DefaultPrettyPrinter?) : super(base)

    override fun writeObjectNameValueSeparator(g: JsonGenerator?) {
      g?.writeRaw(": ")
    }

    override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
      if (!_arrayIndenter.isInline) {
        --_nesting
      }
      if (nrOfValues > 0) {
        _arrayIndenter.writeIndentation(g, _nesting)
      }
      g.writeRaw(']')
    }

    override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
      if (!_objectIndenter.isInline) {
        --_nesting
      }
      if (nrOfEntries > 0) {
        _objectIndenter.writeIndentation(g, _nesting)
      }
      g.writeRaw('}')
    }

    override fun createInstance(): DefaultPrettyPrinter {
      return CustomPrettyPrinter(this)
    }
  }
}
