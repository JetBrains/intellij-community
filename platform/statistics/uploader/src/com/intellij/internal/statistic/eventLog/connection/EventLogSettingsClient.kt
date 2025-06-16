// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo
import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils
import com.intellij.internal.statistic.eventLog.filters.LogEventBucketsFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventCompositeFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventFalseFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventMetadataFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventSnapshotBuildFilter
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationBucketRange
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationReleaseFilter
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationVersion
import com.jetbrains.fus.reporting.model.exceptions.StatsResponseException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.net.ConnectException
import java.net.http.HttpTimeoutException
import java.util.*
import java.util.function.Supplier
import javax.net.ssl.SSLHandshakeException

/**
 * EventLogSettingsClient provides access to content stored in [ConfigurationVersion] for the required
 * product version.
 *
 * To use, it needs to set [configurationClient], [applicationInfo] and [recorderId].
 */
@ApiStatus.Internal
abstract class EventLogSettingsClient {
  abstract val configurationClient: ConfigurationClient
  abstract val applicationInfo: EventLogApplicationInfo
  abstract val recorderId: String

  /**
   * @return true, if configuration versions for the required product version are not empty,
   * false otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun isConfigurationReachable(): Boolean {
    val result: Boolean? = logError {
      configurationClient.isConfigurationReachable()
    }
    applicationInfo.logger.info("Statistics. Configuration url: ${configurationClient.configurationUrl}")
    applicationInfo.logger.info("Statistics. Configuration is reachable: $result")
    return result != null && result
  }

  /**
   * @return true, if configuration versions for the required product version are not empty, the release filters for
   * the required product version are not empty and valid. Release filters aren't valid if from=0,to=Int.MAX
   * or release type is an empty string,
   * false otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun isSendEnabled(): Boolean {
    val result: Boolean? = logError {
      configurationClient.isSendEnabled()
    }
    applicationInfo.logger.info("Statistics. Send is enabled: $result")
    return result != null && result
  }

  /**
   * @return configuration options for the required product version,
   * empty map otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideOptions(): Map<String, String> {
    val result: Map<String, String>? = logError {
      configurationClient.provideOptions()
    }
    applicationInfo.logger.info("Statistics. Configuration options: $result")
    return result ?: emptyMap()
  }

  /**
   * @return endpoint value by required endpoint name for the required product version,
   * null otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideEndpointValue(endpoint: String): String? {
    val result = logError {
      configurationClient.provideEndpointValue(endpoint)
    }
    applicationInfo.logger.info("Statistics. Configuration endpoint: $result")
    return result
  }

  /**
   * @return send endpoint for the required product version,
   * null otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideServiceUrl(): String? {
    val result = logError {
      configurationClient.provideSendEndpoint()
    }
    applicationInfo.logger.info("Statistics. Configuration service url: $result")
    return result
  }

  /**
   * @return dictionary endpoint for the required product version,
   * null otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideDictionaryServiceUrl(): String? {
    val result = logError {
      configurationClient.provideDictionaryEndpoint()
    }
    applicationInfo.logger.info("Statistics. Configuration dictionary service url: $result")
    return result
  }

  /**
   * @return metadata endpoint for the required product version,
   * null otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideMetadataEndpoint(): String? {
    val result = logError {
      configurationClient.provideMetadataEndpoint()
    }
    applicationInfo.logger.info("Statistics. Configuration metadata endpoint: $result")
    return result
  }

  /**
   * @return not-versioned metadata product url for the required product version,
   * null otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideMetadataProductUrl(): String? {
    val result = logError {
      configurationClient.provideMetadataProductUrl()
    }
    applicationInfo.logger.info("Statistics. Configuration metadata product url: $result")
    return result
  }

  /**
   * @return versioned metadata product url for the required product version,
   * null otherwise. Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  fun provideMetadataProductUrl(metadataVersion: Int): String? {
    val result = logError {
      configurationClient.provideMetadataProductUrl(metadataVersion)
    }
    applicationInfo.logger.info("Statistics. Configuration metadata product url: $result")
    return result
  }

  /**
   * @return LogEventFilter for valid release filters bucket ranges by required release type or ALL for the required product version,
   * otherwise LogEventFalseFilter,
   * Release filters aren't valid if from=0,to=Int.MAX or release type is an empty string.
   */
  open fun provideEventFilter(base: LogEventFilter, type: EventLogBuildType): LogEventFilter {
    val bucketRanges = provideBucketRanges(type)
    if (bucketRanges.isEmpty()) {
      val logger = applicationInfo.logger
      if (logger.isTraceEnabled()) {
        logger.trace("Cannot find send bucketRanges for '$type' -> clean up log file")
      }
      return LogEventFalseFilter
    }
    return LogEventCompositeFilter(LogEventBucketsFilter(bucketRanges), base, LogEventSnapshotBuildFilter)
  }

  /**
   * @return LogEventMetadataFilter.
   */
  fun provideBaseEventFilter(metadataVersion: Int? = null): LogEventFilter {
    return LogEventMetadataFilter(notNull(loadApprovedGroupsRules(metadataVersion), EventGroupsFilterRules.empty()))
  }

  /**
   * @return valid release filters bucket ranges by required release type for the required product version,
   * otherwise valid release filters bucket ranges by ALL release type,
   * otherwise empty list.
   * Release filters aren't valid if from=0,to=Int.MAX or release type is empty string.
   * Info/warn exception, log exception to 'recorderId.event.log' group.
   */
  @VisibleForTesting
  fun provideBucketRanges(type: EventLogBuildType): List<ConfigurationBucketRange> {
    val result = logError {
      var conditions = configurationClient.provideReleaseFilters(type.text.uppercase())
      if (conditions.isEmpty()) conditions = configurationClient.provideReleaseFilters(EventLogBuildType.ALL.name)
      if (conditions.isEmpty()) return@logError null

      val bucketRanges: MutableList<ConfigurationBucketRange> = mutableListOf()
      conditions
        .forEach { condition: ConfigurationReleaseFilter ->
          if (condition.provideBucketRange() != null) bucketRanges.add(condition.provideBucketRange()!!)
        }

      return@logError bucketRanges
    }
    return result ?: emptyList()

  }

  private fun loadApprovedGroupsRules(metadataVersion: Int? = null): EventGroupsFilterRules<EventLogBuild>? {
    val productUrl = if (metadataVersion == null) provideMetadataProductUrl() else provideMetadataProductUrl(metadataVersion)
    if (productUrl == null) return null
    return EventLogMetadataUtils.loadAndParseGroupsFilterRules(productUrl, applicationInfo.connectionSettings)
  }

  private fun notNull(groupFilterConditions: EventGroupsFilterRules<EventLogBuild>?, defaultValue: EventGroupsFilterRules<EventLogBuild>):
    EventGroupsFilterRules<EventLogBuild> {
    return groupFilterConditions ?: defaultValue
  }

  private fun <T> logError(supplier: Supplier<out T>): T? {
    try {
      return supplier.get()
    }
    catch (e: Exception) {
      val message = String.format(Locale.ENGLISH, "%s: %s", e.javaClass.name,
                                  Objects.requireNonNullElse(e.message, "No message provided"))

      if (e is ConnectException || e is HttpTimeoutException ||
          e is SSLHandshakeException || e is StatsResponseException) {
        // Expected non-critical problems: no connection, bad connection, errors on loading data
        applicationInfo.getLogger().info(message)
      }
      else {
        applicationInfo.getLogger().warn(message, e)
      }
      applicationInfo.getEventLogger().logLoadingConfigFailed(recorderId, e)
    }
    return null
  }
}