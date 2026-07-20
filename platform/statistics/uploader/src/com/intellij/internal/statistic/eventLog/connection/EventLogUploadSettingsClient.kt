// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection

import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo
import com.intellij.internal.statistic.eventLog.connection.metadata.createJvmHttpClient
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.configuration.RegionCode
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationReleaseFilter
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
import kotlin.reflect.KClass

/**
 * Configuration client wrapper that automatically refreshes the configuration
 * when the cache timeout expires.
 */
class CachedConfigurationClient(
  private val delegate: ConfigurationClient,
  private val cacheTimeoutMs: Long,
  private val maxUpdateAttempts: Int = DEFAULT_MAX_UPDATE_ATTEMPTS
) {
  @Volatile
  private var lastUpdateTime: Long = 0L
  val configurationUrl: String?
    get() = delegate.configurationUrl

  private fun <T> checkAndUpdate(block: () -> T): T {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastUpdateTime >= cacheTimeoutMs) {
      var attempt = 0
      while (attempt < maxUpdateAttempts) {
        attempt++
        if (delegate.update()) {
          // Out of order writing of lastUpdateTime is acceptable.
          // If lastUpdateTime is off by a few millisecond it doesn't really matter because config is almost never updated.
          lastUpdateTime = currentTime
          break
        }
      }
    }
    return block()
  }

  /**
   * @return true, if configuration versions for the required product version are not null, false otherwise.
   * The configuration version is null in case of exceptions.
   * The configuration version is empty if there is no product version in the configuration.
   */
  fun isConfigurationReachable(): Boolean = checkAndUpdate {
    delegate.isConfigurationReachable()
  }

  /**
   * @return true, if configuration versions for the required product version are not null, the release filters for
   * the required product version are not empty and valid. Release filters aren't valid if from=0,to=Int.MAX
   * or release type is an empty string,
   * false otherwise
   */
  fun isSendEnabled(): Boolean = checkAndUpdate {
    delegate.isSendEnabled()
  }

  /**
   * @return configuration options for the required product version,
   * empty map otherwise.
   */
  fun provideOptions(): Map<String, String> = checkAndUpdate {
    delegate.provideOptions()
  }

  /**
   * @return endpoint value by required endpoint name for the required product version,
   * null otherwise.
   */
  fun provideEndpointValue(endpointName: String): String? = checkAndUpdate {
    delegate.provideEndpointValue(endpointName)
  }

  /**
   * @return send endpoint for the required product version,
   * null otherwise.
   */
  fun provideSendEndpoint(): String? = checkAndUpdate {
    delegate.provideSendEndpoint()
  }

  /**
   * @return dictionary endpoint for the required product version,
   * null otherwise
   */
  fun provideDictionaryEndpoint(): String? = checkAndUpdate {
    delegate.provideDictionaryEndpoint()
  }

  /**
   * @return metadata endpoint for the required product version,
   * null otherwise
   */
  fun provideMetadataEndpoint(): String? = checkAndUpdate {
    delegate.provideMetadataEndpoint()
  }

  /**
   * @return versioned metadata product url for the required product version,
   * null otherwise
   */
  fun provideMetadataProductUrl(metadataVersion: Int): String? = checkAndUpdate {
    delegate.provideMetadataProductUrl(metadataVersion)
  }

  /**
   * @return valid release filters by required release type for the required product version,
   * empty list otherwise. Release filters aren't valid if from=0,to=Int.MAX or release type is empty string.
   */
  fun provideReleaseFilters(releaseType: String?): List<ConfigurationReleaseFilter> = checkAndUpdate {
    delegate.provideReleaseFilters(releaseType)
  }

  companion object {
    private const val DEFAULT_MAX_UPDATE_ATTEMPTS: Int = 3
  }
}

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

  private val delegateClient: ConfigurationClient = ConfigurationClientFactory.create(
    httpClient = applicationInfo.connectionSettings.createJvmHttpClient(),
    serializer = FusJacksonSerializer(),
    recorderId = recorderId,
    productCode = applicationInfo.productCode,
    productVersion = applicationInfo.productVersion,
    isTestConfiguration = applicationInfo.isTestConfig,
    regionCode = if (applicationInfo.regionalCode == chinaRegion) RegionCode.CN else RegionCode.ALL
  )
  
  override var configurationClient: CachedConfigurationClient = CachedConfigurationClient(
    delegate = delegateClient,
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

    override fun toJson(data: Any, prettyPrint: Boolean): String = try {
      val serializer = if (prettyPrint) {
        SERIALIZATION_MAPPER
          .writerWithDefaultPrettyPrinter()
      } else {
        SERIALIZATION_MAPPER
          .writer()
      }
      serializer.writeValueAsString(data)
    } catch (e: Exception) {
      throw SerializationException(e)
    }

    override fun <T : Any> fromJson(json: String, clazz: KClass<T>): T = try {
      DESERIALIZATION_MAPPER
        .readValue(json, clazz.java)
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
