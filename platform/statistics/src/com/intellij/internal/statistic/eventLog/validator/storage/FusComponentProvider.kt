// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.internal.statistic.StatisticsServiceScope
import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsListener
import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsService
import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsClient
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateError
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateStage
import com.intellij.internal.statistic.eventLog.validator.rules.utils.CustomRuleProducer
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.idea.AppMode
import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.internal.statistic.config.EventLogOptions
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.util.PlatformUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.fus.reporting.DICTIONARY_LIST_LOAD_FAILED_TOPIC
import com.jetbrains.fus.reporting.DICTIONARY_LIST_UPDATE_FAILED_TOPIC
import com.jetbrains.fus.reporting.DICTIONARY_LOADED_TOPIC
import com.jetbrains.fus.reporting.DICTIONARY_LOAD_FAILED_TOPIC
import com.jetbrains.fus.reporting.DICTIONARY_UPDATED_TOPIC
import com.jetbrains.fus.reporting.DICTIONARY_UPDATE_FAILED_TOPIC
import com.jetbrains.fus.reporting.FileHandle
import com.jetbrains.fus.reporting.FileStorage
import com.jetbrains.fus.reporting.FileStorageMode
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.FusClient
import com.jetbrains.fus.reporting.bundledFileStorage
import com.jetbrains.fus.reporting.components
import com.jetbrains.fus.reporting.config
import com.jetbrains.fus.reporting.eventLogFileStorage
import com.jetbrains.fus.reporting.fileStorage
import com.jetbrains.fus.reporting.fusClient
import com.jetbrains.fus.reporting.httpClient
import com.jetbrains.fus.reporting.jsonSerializer
import com.jetbrains.fus.reporting.loggerFactory
import com.jetbrains.fus.reporting.loggingEnabled
import com.jetbrains.fus.reporting.messageHandler
import com.jetbrains.fus.reporting.metadataStorage
import com.jetbrains.fus.reporting.recordEnabled
import com.jetbrains.fus.reporting.remoteConfig
import com.jetbrains.fus.reporting.reportAnonymizer
import com.jetbrains.fus.reporting.reportDispatcher
import com.jetbrains.fus.reporting.reportValidator
import com.jetbrains.fus.reporting.sendEnabled
import com.jetbrains.fus.reporting.LoadError
import com.jetbrains.fus.reporting.LoadErrorType
import com.jetbrains.fus.reporting.METADATA_LOADED_TOPIC
import com.jetbrains.fus.reporting.METADATA_LOAD_FAILED_TOPIC
import com.jetbrains.fus.reporting.METADATA_UPDATED_TOPIC
import com.jetbrains.fus.reporting.METADATA_UPDATE_FAILED_TOPIC
import com.jetbrains.fus.reporting.MetadataStorage
import com.jetbrains.fus.reporting.REMOTE_CONFIG_OPTIONS_UPDATED
import com.jetbrains.fus.reporting.RAW_EVENT_TOPIC
import com.jetbrains.fus.reporting.RegionCode
import com.jetbrains.fus.reporting.api.IEventGroupRules
import com.jetbrains.fus.reporting.api.IEventGroupsFilterRules
import com.jetbrains.fus.reporting.api.IGroupValidators
import com.jetbrains.fus.reporting.api.RecorderDataValidationRule
import com.jetbrains.fus.reporting.defaults.DefaultMetadataStorage
import com.jetbrains.fus.reporting.defaults.DefaultRemoteConfig
import com.jetbrains.fus.reporting.defaults.MetadataUpdateDelay
import com.jetbrains.fus.reporting.defaults.NoOpLoggerFactory
import com.jetbrains.fus.reporting.defaults.NoOpAnonymizer
import com.jetbrains.fus.reporting.defaults.dispatcher.SimpleLegacyReportDispatcher
import com.jetbrains.fus.reporting.defaults.dispatcher.EventLogBuildType
import com.jetbrains.fus.reporting.defaults.dispatcher.PersistentQueue
import com.jetbrains.fus.reporting.defaults.dispatcher.SEND_INFORMATION_TOPIC
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.LICENSE_CODE_KEY
import com.intellij.internal.statistic.eventLog.MachineId
import com.intellij.internal.statistic.eventLog.StatisticsFileEventLogger
import com.intellij.internal.statistic.eventLog.StatisticsSystemEventIdProvider
import com.intellij.internal.statistic.eventLog.connection.metadata.createJvmHttpClient
import com.intellij.internal.statistic.eventLog.dispatcher.IntellijFusJsonSerializer
import com.intellij.internal.statistic.eventLog.dispatcher.ExternalUploadOrchestrator
import com.intellij.internal.statistic.eventLog.dispatcher.IntellijReportValidator
import com.intellij.internal.statistic.eventLog.events.EventFieldIds
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.REMOTE_CONFIG_OPTIONS_UPDATE_FAILED
import com.jetbrains.fus.reporting.jvm.InMemoryJvmFileStorage
import com.jetbrains.fus.reporting.jvm.JvmFileStorage
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.ValidatedFusReport
import com.jetbrains.fus.reporting.model.serialization.SerializationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
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
import java.io.IOException
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
object FusComponentProvider {
  const val CUSTOM_FUS_SCHEMA_DIR_PROPERTY: String = "intellij.fus.custom.schema.dir"
  const val FUS_METADATA_DIR: String = "event-log-metadata"

  // Mirrors the historical retention used by EventLogFileWriter; PersistentQueue deletes log files older than this.
  private val MAX_LOG_FILE_AGE = 7.days

  @Throws(IOException::class)
  private fun getMetadataDir(recorderId: String): Path = getMetadataConfigRoot()
    .resolve(StringUtil.toLowerCase(recorderId)) // TODO: can we remove lower case?
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

  data class FusComponents(
    val metadataStorage: MetadataStorage<EventLogBuild>,
    /**
     * Null only on the blind/test path ([createBlindFusComponents]) — production [createFusComponents] always builds a real
     * client. Callers in the IDE's event pipeline can `!!` it; unit tests typically route through
     * `TestStatisticsEventLoggerProvider` and never touch this field.
     */
    val fusClient: FusClient<LogEvent, ValidatedFusReport>? = null,
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
    override suspend fun reload() = Unit
    override suspend fun scheduleUpdate() = Unit
    override suspend fun update(): Boolean = false
    override fun getClientDataRulesRevisions(): RecorderDataValidationRule = throw UnsupportedOperationException("Not supported")
    override fun getFieldsToAnonymize(groupId: String, eventId: String): Set<String> = emptySet()
  }

  @JvmStatic
  fun createBlindFusComponents(recorderId: String): FusComponents {
    return FusComponents(
      metadataStorage = CompositeValidationRulesStorage(
        metadataStorage = BlindMetadataStorage(),
        testRulesStorage = ValidationTestRulesPersistedStorage(recorderId)
      )
    )
  }

  @JvmStatic
  fun createFusComponents(
    recorderId: String
  ): FusComponents {
    val applicationInfo = EventLogInternalApplicationInfo(
      StatisticsUploadAssistant.isUseTestStatisticsConfig(),
      StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint()
    )
    val eventLogProvider = getEventLogProvider(recorderId)
    val isUnitTest = ApplicationManager.getApplication().isUnitTestMode()

    // TODO: change isInternal to lambda so it is evaluated dynamically
    val isInternal = applicationInfo.isInternal
    val systemLogGroupId = "${recorderId.lowercase(Locale.ENGLISH)}.event.log"
    val systemCollector = eventLogProvider.eventLogSystemLogger
    val recorderConfig = EventLogConfiguration.getInstance()
      .getOrCreate(recorderId = recorderId, alternativeRecorderId = if (eventLogProvider.useDefaultRecorderId) "FUS" else null)
    val device = recorderConfig.deviceId
    val machineId = recorderConfig.machineId

    // Inputs for the dispatcher's preEventWrite hook (system-field injection, moved out of StatisticsFileEventLogger).
    val isHeadless = ApplicationManager.getApplication()?.isHeadlessEnvironment == true
    val ideMode = if (AppMode.isRemoteDevHost()) "RDH" else null
    val currentProductModeId = ProductLoadingStrategy.strategy.currentModeId
    val productMode = when {
      PlatformUtils.isQodana() -> null
      currentProductModeId != ProductMode.MONOLITH.id -> currentProductModeId
      detectClionNova() -> "nova"
      else -> null
    }
    val systemEventIdProvider = UsageStatisticsPersistenceComponent.getInstance()

    // The metadata storage is built inside the DSL `metadataStorage { }` provider (it needs the SDK-built bus /
    // remote config / file storage). We capture it here so the same instance can back IntellijSensitiveDataValidator.
    var metadataStorageRef: MetadataStorage<EventLogBuild>? = null

    val client = fusClient {
      parentScope = StatisticsServiceScope.getScope() + Dispatchers.IO // make sure all FUS schedulers run on Dispatchers.IO

      config {
        productName = ApplicationNamesInfo.getInstance().fullProductName
        productCode = applicationInfo.productCode
        recorderCode = recorderId
        recorderVersion = eventLogProvider.version.toString()
        regionCode = if (applicationInfo.regionalCode == EventLogUploadSettingsClient.chinaRegion) RegionCode.CN else RegionCode.ALL
        productVersion = applicationInfo.productVersion
        baselineVersion = applicationInfo.baselineVersion
        anonymizationSalt = null // IntelliJ doesn't use anonymization from reporting SDK
        isTest = applicationInfo.isTestConfig
        reduceInitialMetadataUpdateDelay = System.getProperty("fus.internal.reduce.initial.delay").toBoolean()
        // Keep event enqueue synchronous and ordered, matching the legacy logger -> writer chain.
        enableAsyncEventLogging = false

        loggingEnabled { eventLogProvider.isLoggingEnabled() }
        recordEnabled { eventLogProvider.isRecordEnabled() }
        sendEnabled { eventLogProvider.isSendEnabled() }
      }

      messageHandler(REMOTE_CONFIG_OPTIONS_UPDATED) { updateOptions(recorderId, it) }
      messageHandler(METADATA_LOADED_TOPIC) { systemCollector.logMetadataLoaded(it) }
      messageHandler(METADATA_LOAD_FAILED_TOPIC) { systemCollector.logMetadataLoadFailed(loadErrorToEventLogMetadataUpdateError(it)) }
      messageHandler(METADATA_UPDATED_TOPIC) { systemCollector.logMetadataUpdated(it) }
      messageHandler(METADATA_UPDATE_FAILED_TOPIC) { systemCollector.logMetadataUpdateFailed(loadErrorToEventLogMetadataUpdateError(it)) }
      messageHandler(DICTIONARY_LIST_LOAD_FAILED_TOPIC) { systemCollector.logDictionaryListLoadFailed(loadErrorToEventLogMetadataUpdateError(it)) }
      messageHandler(DICTIONARY_LIST_UPDATE_FAILED_TOPIC) { systemCollector.logDictionaryListUpdateFailed(loadErrorToEventLogMetadataUpdateError(it)) }
      messageHandler(DICTIONARY_LOADED_TOPIC) { systemCollector.logDictionaryLoaded(it.timestamp) }
      messageHandler(DICTIONARY_LOAD_FAILED_TOPIC) { systemCollector.logDictionaryLoadFailed(loadErrorToEventLogMetadataUpdateError(it)) }
      messageHandler(DICTIONARY_UPDATED_TOPIC) { systemCollector.logDictionaryUpdated(it.timestamp) }
      messageHandler(DICTIONARY_UPDATE_FAILED_TOPIC) { systemCollector.logDictionaryUpdateFailed(loadErrorToEventLogMetadataUpdateError(it)) }

      val listenersManager = ApplicationManager.getApplication().getService(EventLogListenersManager::class.java)
      val testMode = StatisticsRecorderUtil.isTestModeEnabled(recorderId)
      messageHandler(RAW_EVENT_TOPIC) { fusEvent ->
        val recorderHasJcpListener = service<EventLogListenersManager>().hasJcpListener(recorderId)
        val keepRawData = testMode || recorderHasJcpListener
        val event = fusEvent.event as? LogEvent ?: return@messageHandler
        listenersManager.notifySubscribers(
          recorderId,
          event,
          if (keepRawData) fusEvent.rawEventId else null,
          if (keepRawData) fusEvent.rawEventData else null,
          false,
        )
      }

      messageHandler(SEND_INFORMATION_TOPIC) { info ->
        systemCollector.logFilesSend(
          total = info.totalAmountOfBatches ?: (info.successfulBatches + info.failedBatches),
          succeed = info.successfulBatches,
          failed = info.failedBatches,
          external = false,
          successfullySentFiles = info.paths.toList(),
          errors = info.errorCodes.mapNotNull { it.toIntOrNull() },
        )
      }

      components {
        loggerFactory { NoOpLoggerFactory() }
        httpClient { _ -> applicationInfo.connectionSettings.createJvmHttpClient() }
        fileStorage { if (isUnitTest) InMemoryJvmFileStorage() else JvmFileStorage(getMetadataDir(recorderId)) }
        bundledFileStorage { BundledJvmFileStorage(recorderId) }
        eventLogFileStorage { if (isUnitTest) InMemoryJvmFileStorage() else JvmFileStorage(getEventLogDir(recorderId)) }
        jsonSerializer { IntellijFusJsonSerializer(FusJacksonSerializer()) }
        remoteConfig { config, messageBus, loggerFactory, httpClient, jsonSerializer, _ ->
          DefaultRemoteConfig(config, messageBus, loggerFactory, jsonSerializer, httpClient)
        }
        metadataStorage { config, messageBus, loggerFactory, remoteConfig, httpClient, fileStorage, jsonSerializer, bundledFileStorage ->
          val storage = DefaultMetadataStorage(
            config,
            messageBus,
            loggerFactory,
            remoteConfig,
            httpClient,
            jsonSerializer,
            fileStorage,
            bundledFileStorage,
            MetadataUpdateDelay.LONG,
            { version -> EventLogBuild.fromString(version) },
            excludedFields = FeatureUsageData.platformDataKeys,
            utilRulesProducer = CustomRuleProducer(recorderId)
          )
          val effective: MetadataStorage<EventLogBuild> = if (isInternal) {
            CompositeValidationRulesStorage(storage, ValidationTestRulesPersistedStorage(recorderId))
          } else {
            storage
          }
          metadataStorageRef = effective
          effective
        }
        reportAnonymizer { _, _ -> NoOpAnonymizer() }
        reportValidator { _ -> IntellijReportValidator(recorderId) }
        reportDispatcher { config, messageBus, remoteConfig, httpClient, jsonSerializer, eventLogFileStorage, loggerFactory, _, validator ->
          val buildType = if (applicationInfo.isEAP) EventLogBuildType.EAP else EventLogBuildType.RELEASE
          val persistentQueue = PersistentQueue(
            messageBus = messageBus,
            fileStorage = eventLogFileStorage,
            jsonSerializer = jsonSerializer,
            loggerFactory = loggerFactory,
            defaultDelay = eventLogProvider.sendFrequencyMs.milliseconds,
            eventClass = LogEvent::class,
            buildType = buildType,
            maxFileBytes = eventLogProvider.maxFileSizeInBytes.toLong(),
            maxFileAge = MAX_LOG_FILE_AGE,
          )
          SimpleLegacyReportDispatcher(
            messageBus,
            config,
            remoteConfig,
            jsonSerializer,
            httpClient,
            loggerFactory,
            validator,
            persistentQueue,
            device,
            isInternal,
            systemLogGroupId,
            eventLogProvider.sendFrequencyMs.milliseconds,
            5000,
            eventLogProvider.isCharsEscapingRequired,
            EventFieldIds.FieldsIgnoredByMerge.toSet()
          ) {
            val lastEventTime = AtomicLong(0L)
            val lastEventCreatedTime = AtomicLong(0L)
            preEventWrite = { event ->
              lastEventCreatedTime.compareAndSet(0L, event.time)
              event.also {
                applyFusEventExtensions(
                  it,
                  recorderId,
                  systemEventIdProvider,
                  isHeadless,
                  ideMode,
                  productMode,
                  lastEventTime.get(),
                  lastEventCreatedTime.get()
                )
                lastEventTime.set(event.time)
                if (!event.event.isEventGroup()) {
                  lastEventCreatedTime.set(System.currentTimeMillis())
                }
              }
            }
            preEventsSend = { events ->
              val machineId = actualOrDisabledMachineId(machineId, remoteConfig.provideOptions())
              // Legacy EventLogStatisticsService applied provideEventFilter(...) per send round; buckets/sampling
              // moved into the SDK, the remaining parts (approved-groups re-check + snapshot-build filter) live here.
              val testMode = StatisticsRecorderUtil.isTestModeEnabled(recorderId)
              events
                .filter { isNotSnapshotBuild(it) && isGroupApprovedForSend(it, metadataStorageRef, testMode) }
                .onEach { fillMachineId(it, machineId) }
            }
            // Start the out-of-process external uploader on IDE shutdown (was IntellijReportDispatcher.postClose).
            postClose = { ExternalUploadOrchestrator.tryStartExternalUpload() }
          }
        }
      }
    }

    return FusComponents(metadataStorage = metadataStorageRef!!, fusClient = client)
  }

  /**
   * Send-time machine-id resolution, formerly [com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService]'s
   * `getActualOrDisabledMachineId`: the remote config can disable machine-id reporting via the `id_salt` option,
   * so the decision must be made on every upload, not when the event is written.
   */
  private fun actualOrDisabledMachineId(machineId: MachineId, options: Map<String, String>): MachineId {
    if (machineId == MachineId.DISABLED) return MachineId.DISABLED
    if (options[EventLogOptions.MACHINE_ID_SALT] == EventLogOptions.MACHINE_ID_DISABLED) return MachineId.DISABLED
    return machineId
  }

  /** Formerly [com.intellij.internal.statistic.eventLog.LogEventRecordRequest.fillMachineId]. */
  private fun fillMachineId(event: LogEvent, machineId: MachineId) {
    event.event.data["system_machine_id"] = machineId.id
    if (machineId.revision != EventLogOptions.DEFAULT_ID_REVISION &&
        machineId != MachineId.UNKNOWN &&
        machineId != MachineId.DISABLED) {
      event.event.data["system_id_revision"] = machineId.revision
    }
  }

  /**
   * Formerly `LogEventSnapshotBuildFilter`: never upload events recorded by snapshot builds
   * (`XXX.0`) or with an unparseable build number.
   */
  private fun isNotSnapshotBuild(event: LogEvent): Boolean {
    val disabled = Registry.`is`("feature.usage.event.snapshot.filtering.disabled", false)
    if (disabled) {
      return true
    }
    val parts = EventLogBuild.fromString(event.build)?.components ?: return false
    return parts.size != 2 || parts[1] != 0
  }

  /**
   * Formerly `LogEventMetadataFilter` (built from `provideBaseEventFilter`): re-checks group approval against the
   * *current* metadata at send time. Events can sit in the queue for days; a group de-listed in between must not
   * be uploaded, even though it passed write-time validation.
   *
   * Deviations from legacy, both deliberate:
   * - unreachable metadata keeps events (legacy dropped everything via `EventGroupsFilterRules.empty()`;
   *   the SDK storage has persisted/bundled fallbacks, so unreachable is a degraded state, not the steady state);
   * - test-mode recorders and test-rule groups (versionFilter == null but eventGroupRules != null,
   *   see [CompositeValidationRulesStorage]) always pass, mirroring `IntellijSensitiveDataValidator.isGroupAllowed`.
   */
  private fun isGroupApprovedForSend(
    event: LogEvent,
    metadataStorage: MetadataStorage<EventLogBuild>?,
    testMode: Boolean,
  ): Boolean {
    if (testMode) return true
    if (metadataStorage?.isUnreachable() ?: true) return true
    val validators = metadataStorage.getGroupValidators(event.group.id)
    val versionFilter = validators.versionFilter
                        ?: return validators.eventGroupRules != null // test-rule/custom-path group vs. unknown group
    return versionFilter.accepts(event.group.id, event.group.version, event.build)
  }

  /**
   * Injects the per-event "system" fields onto [event]. Formerly done inline in [StatisticsFileEventLogger];
   * now invoked from the SDK dispatcher's `preEventWrite` hook (see `FusComponentProvider.createFusComponents`), so every
   * queued event (including throttle-generated ones) is augmented exactly once.
   */
  private fun applyFusEventExtensions(
    event: LogEvent,
    recorderId: String,
    systemEventIdProvider: StatisticsSystemEventIdProvider,
    headless: Boolean,
    ideMode: String?,
    productMode: String?,
    lastEventTime: Long,
    lastEventCreatedTime: Long
  ) {
    val data = event.event.data
    if (event.event.isEventGroup()) {
      data["last"] = lastEventTime
    }
    data["created"] = lastEventCreatedTime
    var systemEventId = systemEventIdProvider.getSystemEventId(recorderId)
    data["system_event_id"] = systemEventId
    systemEventIdProvider.setSystemEventId(recorderId, ++systemEventId)
    if (headless) data["system_headless"] = true
    if (ideMode != null) data["ide_mode"] = ideMode
    if (productMode != null) data["product_mode"] = productMode
    ApplicationManager.getApplication().getUserData(LICENSE_CODE_KEY)?.let { data["auto_license_type"] = it }
  }

  private fun getEventLogDir(recorderId: String): Path =
    EventLogConfiguration.getInstance().getEventLogDataPath().resolve("logs").resolve(recorderId)

  // Taken from CLionLanguagePluginKind; remove once CLion Nova is deployed 100%.
  private fun detectClionNova(): Boolean =
    System.getProperty("idea.suppressed.plugins.set.selector") == "radler" && PlatformUtils.isCLion()

  class BundledJvmFileStorage(private val recorderId: String) : FileStorage {
    private val bundledBasePath: String
      get() = "$FUS_METADATA_DIR/$recorderId/"

    private fun bundledResourcePath(path: String): String = if (path.startsWith('/')) {
      bundledBasePath + path.substring(1)
    } else {
      "$bundledBasePath/$path"
    }

    // getResource returns `null` if resource is not found
    override fun exists(path: String): Boolean = this.javaClass.classLoader.getResource(bundledResourcePath(path)) != null
    override fun list(path: String): List<String> = emptyList()
    override fun delete(path: String): Unit = Unit

    // bundled file storage does not support random file access
    override fun openFileHandle(path: String, mode: FileStorageMode): FileHandle = object : FileHandle {
      override val name: String
        get() = path

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
        SERIALIZATION_MAPPER.writer()
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
