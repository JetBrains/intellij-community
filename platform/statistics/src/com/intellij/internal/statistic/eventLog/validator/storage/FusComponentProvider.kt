// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsClient
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateError
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateStage
import com.intellij.internal.statistic.eventLog.validator.IEventGroupRules
import com.intellij.internal.statistic.eventLog.validator.IEventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.validator.IGroupValidators
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RecorderDataValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.utils.CustomRuleProducer
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.fus.reporting.*
import com.jetbrains.fus.reporting.defaults.DefaultMetadataStorage
import com.jetbrains.fus.reporting.defaults.DefaultRemoteConfig
import com.jetbrains.fus.reporting.defaults.MetadataUpdateDelay
import com.jetbrains.fus.reporting.defaults.NoOpLoggerFactory
import com.jetbrains.fus.reporting.jvm.InMemoryJvmFileStorage
import com.jetbrains.fus.reporting.jvm.JvmFileStorage
import com.jetbrains.fus.reporting.jvm.JvmHttpClient
import com.jetbrains.fus.reporting.jvm.ProxyInfo
import com.jetbrains.fus.reporting.model.serialization.SerializationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

@ApiStatus.Internal
object FusComponentProvider {
  const val CUSTOM_FUS_SCHEMA_DIR_PROPERTY: String = "intellij.fus.custom.schema.dir"
  const val FUS_METADATA_DIR: String = "event-log-metadata"

  @Throws(IOException::class)
  private fun getMetadataDir(recorderId: String): Path = getMetadataConfigRoot()
    .resolve(StringUtil.toLowerCase(recorderId))
    .toAbsolutePath()

  private fun getMetadataConfigRoot(): Path {
    val customFusPath = System.getProperty(CUSTOM_FUS_SCHEMA_DIR_PROPERTY)
    if (!StringUtil.isEmpty(customFusPath)) {
      return Path.of(customFusPath)
    }

    return PathManager.getConfigDir().resolve(FUS_METADATA_DIR)
  }

  private fun loadErrorToEventLogMetadataUpdateError(loadError: LoadError) = when(loadError.errorType) {
    LoadErrorType.LOADING -> object : EventLogMetadataUpdateError {
      override fun getErrorType(): String = EventLogMetadataLoadException.EventLogMetadataLoadErrorType.ERROR_ON_LOAD.name
      override fun getErrorCode(): Int = loadError.statusCode
      override fun getUpdateStage(): EventLogMetadataUpdateStage = EventLogMetadataUpdateStage.LOADING
    }
    LoadErrorType.PARSING -> object : EventLogMetadataUpdateError {
      override fun getErrorType(): String = EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON.name
      override fun getErrorCode(): Int = loadError.statusCode
      override fun getUpdateStage(): EventLogMetadataUpdateStage = EventLogMetadataUpdateStage.PARSING
    }
    LoadErrorType.UNKNOWN_IO -> object : EventLogMetadataUpdateError {
      override fun getErrorType(): String = EventLogMetadataLoadException.EventLogMetadataLoadErrorType.UNKNOWN_IO_ERROR.name
      override fun getErrorCode(): Int = loadError.statusCode
      override fun getUpdateStage(): EventLogMetadataUpdateStage = EventLogMetadataUpdateStage.LOADING
    }
  }

  fun updateOptions(recorderId: String, options: Map<String, String>) {
    val persisted = EventLogMetadataSettingsPersistence.getInstance()
    val changedOptions = persisted.updateOptions(recorderId, options)
    if (!changedOptions.isEmpty()) {
      ApplicationManager.getApplication().getMessageBus()
        .syncPublisher<EventLogConfigOptionsListener>(EventLogConfigOptionsService.TOPIC)
        .optionsChanged(recorderId, changedOptions)
    }
  }

  fun CoroutineScope.listenToOptionsChanges(recorderId: String, messageBus: MessageBus) {
    MessageHandler(messageBus, this, REMOTE_CONFIG_OPTIONS_UPDATED) { options ->
      updateOptions(recorderId, options)
    }
  }

  fun CoroutineScope.listenToMetadataEvents(recorderId: String, messageBus: MessageBus) {
    val systemCollector = getEventLogProvider(recorderId).eventLogSystemLogger

    MessageHandler(messageBus, this, METADATA_LOADED_TOPIC) { version ->
      systemCollector.logMetadataLoaded(version)
    }

    MessageHandler(messageBus, this, METADATA_LOAD_FAILED_TOPIC) { loadError ->
      systemCollector.logMetadataLoadFailed(loadErrorToEventLogMetadataUpdateError(loadError))
    }

    MessageHandler(messageBus, this, METADATA_UPDATED_TOPIC) { version ->
      systemCollector.logMetadataUpdated(version)
    }

    MessageHandler(messageBus, this, METADATA_UPDATE_FAILED_TOPIC) { loadError ->
      systemCollector.logMetadataUpdateFailed(loadErrorToEventLogMetadataUpdateError(loadError))
    }

    MessageHandler(messageBus, this, DICTIONARY_LIST_LOAD_FAILED_TOPIC) { loadError ->
      systemCollector.logDictionaryListLoadFailed(loadErrorToEventLogMetadataUpdateError(loadError))
    }

    MessageHandler(messageBus, this, DICTIONARY_LIST_UPDATE_FAILED_TOPIC) { loadError ->
      systemCollector.logDictionaryListUpdateFailed(loadErrorToEventLogMetadataUpdateError(loadError))
    }

    MessageHandler(messageBus, this, DICTIONARY_LOADED_TOPIC) { update ->
      systemCollector.logDictionaryLoaded(update.timestamp)
    }

    MessageHandler(messageBus, this, DICTIONARY_LOAD_FAILED_TOPIC) { loadError ->
      systemCollector.logDictionaryLoadFailed(loadErrorToEventLogMetadataUpdateError(loadError))
    }

    MessageHandler(messageBus, this, DICTIONARY_UPDATED_TOPIC) { update ->
      systemCollector.logDictionaryUpdated(update.timestamp)
    }

    MessageHandler(messageBus, this, DICTIONARY_UPDATE_FAILED_TOPIC) { loadError ->
      systemCollector.logDictionaryUpdateFailed(loadErrorToEventLogMetadataUpdateError(loadError))
    }
  }

  data class FusComponents(
    val metadataStorage: MetadataStorage<EventLogBuild>,
    val messageBus: MessageBus,
    val remoteConfig: RemoteConfig,
  )

  private class BlindMetadataStorage:  MetadataStorage<EventLogBuild> {
    override fun getIdsRulesRevisions(): RecorderDataValidationRule = throw UnsupportedOperationException("Not supported")
    override fun getSystemDataRulesRevisions(): RecorderDataValidationRule = throw UnsupportedOperationException("Not supported")
    override fun isUnreachable(): Boolean = false
    override fun getGroupValidators(groupId: String): IGroupValidators<EventLogBuild> {
      return object : IGroupValidators<EventLogBuild> {
        override val eventGroupRules: IEventGroupRules? = null
        override val versionFilter: IEventGroupsFilterRules<EventLogBuild>? = null
      }
    }
    override fun getSkipAnonymizationIds(): Set<String> = emptySet()
    override fun reload() = Unit
    override fun update(): Boolean = false
    override suspend fun update(scope: CoroutineScope): Job = throw UnsupportedOperationException("Not supported")
    override fun getClientDataRulesRevisions(): RecorderDataValidationRule = throw UnsupportedOperationException("Not supported")
    override fun getFieldsToAnonymize(groupId: String, eventId: String): Set<String> = emptySet()
  }

  private class BlindRemoteConfig : RemoteConfig {
    override fun getSendUrl(): String = ""
    override fun provideOptions(): Map<String, String> = emptyMap()
    override fun getMetadataUrl(): String = ""
    override fun getDictionaryUrl(): String = ""
  }

  @JvmStatic
  fun createBlindFusComponents(recorderId: String): FusComponents {
    return FusComponents(
      CompositeValidationRulesStorage(
        BlindMetadataStorage(),
        ValidationTestRulesPersistedStorage(recorderId)
      ),
      MessageBus(),
      BlindRemoteConfig()
    )
  }

  @JvmStatic
  fun createFusComponents(recorderId: String): FusComponents {
    val applicationInfo = EventLogInternalApplicationInfo(
      StatisticsUploadAssistant.isUseTestStatisticsConfig(),
      StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint()
    )

    val messageBus = MessageBus()

    val config = FusClientConfig(
      applicationInfo.productCode,
      applicationInfo.productCode,
      recorderId,
      if (applicationInfo.regionalCode == EventLogUploadSettingsClient.chinaRegion) RegionCode.CN else RegionCode.ALL,
      applicationInfo.productVersion,
      applicationInfo.baselineVersion,
      null, // IntelliJ doesn't use anonymization from reporting SDK
      applicationInfo.isTestConfig,
      System.getProperty("fus.internal.reduce.initial.delay").toBoolean()
    )

    val jsonSerializer = FusJacksonSerializer()

    val httpClient = JvmHttpClient(
      sslContextProvider = { applicationInfo.connectionSettings.provideSSLContext() },
      proxyProvider = { configurationUrl ->
        ProxyInfo(applicationInfo.connectionSettings.provideProxy(configurationUrl).proxy)
      },
      extraHeadersProvider = { applicationInfo.connectionSettings.provideExtraHeaders() },
      userAgent = applicationInfo.connectionSettings.provideUserAgent()
    )

    val remoteConfig = DefaultRemoteConfig(
      config,
      jsonSerializer,
      httpClient
    )

    val fileStorage = if (ApplicationManager.getApplication().isUnitTestMode()) {
      InMemoryJvmFileStorage()
    } else {
      JvmFileStorage(getMetadataDir(recorderId))
    }

    val metadataStorage = DefaultMetadataStorage(
      config,
      messageBus,
      NoOpLoggerFactory(),
      remoteConfig,
      httpClient,
      jsonSerializer,
      fileStorage,
      BundledJvmFileStorage(recorderId),
      MetadataUpdateDelay.LONG,
      { version -> EventLogBuild.fromString(version) },
      excludedFields = FeatureUsageData.platformDataKeys,
      utilRulesProducer = CustomRuleProducer(recorderId)
    )

    return FusComponents(
      if (ApplicationManager.getApplication().isInternal()) {
        CompositeValidationRulesStorage(
          metadataStorage,
          ValidationTestRulesPersistedStorage(recorderId)
        )
      } else {
        metadataStorage
      },
      messageBus,
      remoteConfig
    )
  }

  class BundledJvmFileStorage(private val recorderId: String) : FileStorage {
    private val bundledBasePath: String
      get() = "resources/$FUS_METADATA_DIR/$recorderId/"

    private fun bundledResourcePath(path: String): String = if (path.startsWith('/')) {
      bundledBasePath + path.substring(1)
    } else {
      "$bundledBasePath/$path"
    }

    // getResource returns `null` if resource is not found
    override fun exists(path: String): Boolean = this.javaClass.classLoader.getResource(bundledResourcePath(path)) != null

    // bundled file storage does not support random file access
    override fun openFileHandle(path: String, mode: FileStorageMode): FileHandle = object : FileHandle {
      override fun exists(): Boolean = false
      override fun length(): Long = 0
      override fun read(index: Int): Byte = 0
      override fun read(index: Int, dstBuffer: ByteArray): ByteArray = ByteArray(0)
      override fun readAll(): ByteArray = ByteArray(0)
      override fun write(bytes: ByteArray) = Unit
      override fun close() = Unit
    }

    override fun read(path: String): ByteArray? = this.javaClass.classLoader.getResourceAsStream(bundledResourcePath(path))?.use {
      it.readAllBytes()
    }

    // writing files to bundled storage is not supported
    override fun write(path: String, content: ByteArray): Unit = Unit
  }

  class FusJacksonSerializer: FusJsonSerializer {
    private val SERIALIZATION_MAPPER: JsonMapper by lazy {
      JsonMapper
        .builder()
        .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .defaultPrettyPrinter(CustomPrettyPrinter())
        .build()
    }

    private val DESERIALIZATION_MAPPER: JsonMapper by lazy {
      JsonMapper
        .builder()
        .enable(DeserializationFeature.USE_LONG_FOR_INTS)
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
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

    override fun writeObjectFieldValueSeparator(g: JsonGenerator) {
      g.writeRaw(": ")
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
