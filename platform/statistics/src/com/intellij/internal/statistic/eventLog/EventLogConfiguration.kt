// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.application.subscribe
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.internal.statistic.config.EventLogOptions.DEFAULT_ID_REVISION
import com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_DISABLED
import com.intellij.internal.statistic.eventLog.EventLogConfiguration.Companion.UNDEFINED_DEVICE_ID
import com.intellij.internal.statistic.eventLog.EventLogConfiguration.Companion.generateSessionId
import com.intellij.internal.statistic.eventLog.EventLogConfiguration.Companion.hashSha256
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.MathUtil
import com.intellij.util.io.DigestUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.*
import java.util.prefs.Preferences

@ApiStatus.Internal
@Service(Service.Level.APP)
class EventLogConfiguration {
  companion object {
    internal val LOG = Logger.getInstance(EventLogConfiguration::class.java)

    internal const val UNDEFINED_DEVICE_ID = "000000000000000-0000-0000-0000-000000000000"

    private const val FUS_RECORDER = "FUS"
    private const val SALT_PREFERENCE_KEY = "feature_usage_event_log_salt"
    private const val IDEA_HEADLESS_STATISTICS_DEVICE_ID = "idea.headless.statistics.device.id"
    private const val IDEA_HEADLESS_STATISTICS_SALT = "idea.headless.statistics.salt"
    private const val IDEA_HEADLESS_STATISTICS_MAX_FILES_TO_SEND = "idea.headless.statistics.max.files.to.send"

    @JvmStatic
    fun getInstance(): EventLogConfiguration = ApplicationManager.getApplication().getService(EventLogConfiguration::class.java)

    /**
     * Don't use this method directly, prefer [EventLogConfiguration.anonymize]
     */
    fun hashSha256(salt: ByteArray, data: String): String {
      val md = DigestUtil.sha256()
      md.update(salt)
      md.update(data.toByteArray())
      return StringUtil.toHexString(md.digest())
    }

    public fun getOrGenerateSaltFromPrefs(recorderId: String): ByteArray{
      val companyName = ApplicationInfoImpl.getShadowInstance().shortCompanyName
      val name = if (StringUtil.isEmptyOrSpaces(companyName)) "jetbrains" else companyName.toLowerCase(Locale.US)
      val prefs = Preferences.userRoot().node(name)

      val saltKey = getSaltPropertyKey(recorderId)
      var salt = prefs.getByteArray(saltKey, null)
      if (salt == null) {
        salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        prefs.putByteArray(saltKey, salt)
        LOG.info("Generating new salt for $recorderId")
      }
      return salt
    }

    val defaultSessionId: String by lazy { generateSessionId() }
    internal fun generateSessionId(): String {
      val presentableHour = StatisticsUtil.getCurrentHourInUTC()
      return "$presentableHour-${UUID.randomUUID().toString().shortedUUID()}"
    }

    private fun String.shortedUUID(): String {
      val start = this.lastIndexOf('-')
      if (start > 0 && start + 1 < this.length) {
        return this.substring(start + 1)
      }
      return this
    }

    private fun isDefaultRecorderId(recorderId: String): Boolean {
      return FUS_RECORDER == recorderId
    }

    internal fun getSaltPropertyKey(recorderId: String): String {
      return if (isDefaultRecorderId(recorderId)) SALT_PREFERENCE_KEY else StringUtil.toLowerCase(recorderId) + "_" + SALT_PREFERENCE_KEY
    }
  }

  private val defaultConfiguration = EventLogRecorderConfiguration(FUS_RECORDER, this, defaultSessionId)
  private val configurations: MutableMap<String, EventLogRecorderConfiguration> = HashMap()

  val build: String by lazy { ApplicationInfo.getInstance().build.asBuildNumber() }

  val bucket: Int = defaultConfiguration.bucket

  @Deprecated("Call method on configuration created with getOrCreate method")
  fun anonymize(data: String): String {
    return defaultConfiguration.anonymize(data)
  }

  private fun BuildNumber.asBuildNumber(): String {
    val str = this.asStringWithoutProductCodeAndSnapshot()
    return if (str.endsWith(".")) str + "0" else str
  }

  fun getOrCreate(recorderId: String): EventLogRecorderConfiguration {
    if (isDefaultRecorderId(recorderId)) return defaultConfiguration

    synchronized(this) {
      return configurations.getOrPut(recorderId) { EventLogRecorderConfiguration(recorderId, this) }
    }
  }

  fun getEventLogDataPath(): Path = Paths.get(PathManager.getSystemPath()).resolve("event-log-data")

  fun getEventLogSettingsPath(): Path = getEventLogDataPath().resolve("settings")

  internal fun getHeadlessDeviceIdProperty(recorderId: String): String {
    return getRecorderBasedProperty(recorderId, IDEA_HEADLESS_STATISTICS_DEVICE_ID)
  }

  internal fun getHeadlessSaltProperty(recorderId: String): String {
    return getRecorderBasedProperty(recorderId, IDEA_HEADLESS_STATISTICS_SALT)
  }

  internal fun getHeadlessMaxFilesToSendProperty(recorderId: String): String {
    return getRecorderBasedProperty(recorderId, IDEA_HEADLESS_STATISTICS_MAX_FILES_TO_SEND)
  }

  private fun getRecorderBasedProperty(recorderId: String, property: String): String {
    return if (isDefaultRecorderId(recorderId)) property else property + "." + StringUtil.toLowerCase(recorderId)
  }

}

class EventLogRecorderConfiguration internal constructor(private val recorderId: String,
                                                         private val eventLogConfiguration: EventLogConfiguration,
                                                         val sessionId: String = generateSessionId()) {

  val deviceId: String = getOrGenerateDeviceId()
  val bucket: Int = deviceId.asBucket()

  private val salt: ByteArray = getOrGenerateSalt()
  private val anonymizedCache: AnonymizedIdsCache = AnonymizedIdsCache()
  private val machineIdReference: AtomicLazyValue<MachineId>

  val machineId: MachineId
    get() = machineIdReference.getValue()

  val maxFilesToSend: Int = getMaxFilesToSend()

  init {
    val configOptions = EventLogConfigOptionsService.getInstance().getOptions(recorderId)
    machineIdReference = AtomicLazyValue { generateMachineId(configOptions.machineIdSalt, configOptions.machineIdRevision) }

    EventLogConfigOptionsService.TOPIC.subscribe(null, object : EventLogRecorderConfigOptionsListener(recorderId) {
      override fun onMachineIdConfigurationChanged(salt: @Nullable String?, revision: Int) {
        machineIdReference.updateAndGet { prevValue ->
          if (salt != null && revision != -1 && revision > prevValue.revision) {
            generateMachineId(salt, revision)
          }
          else prevValue
        }
      }
    })
  }

  private fun generateMachineId(machineIdSalt: String?, value: Int): MachineId {
    val salt = machineIdSalt ?: ""
    if (salt == MACHINE_ID_DISABLED) {
      return MachineId.DISABLED
    }
    val revision = if (value >= 0) value else DEFAULT_ID_REVISION
    val machineId = MachineIdManager.getAnonymizedMachineId("JetBrains$recorderId", salt)
    if (machineId == null) {
      return MachineId.UNKNOWN
    }
    return MachineId(machineId, revision)
  }

  fun anonymize(data: String): String {
    if (data.isBlank()) {
      return data
    }

    return anonymizedCache.computeIfAbsent(data) { hashSha256(salt, it) }
  }

  private fun String.asBucket(): Int {
    return MathUtil.nonNegativeAbs(this.hashCode()) % 256
  }

  private fun getOrGenerateDeviceId(): String {
    val app = ApplicationManager.getApplication()
    if (app != null && app.isHeadlessEnvironment) {
      val property = eventLogConfiguration.getHeadlessDeviceIdProperty(recorderId)
      System.getProperty(property)?.let {
        return it
      }
    }

    try {
      return DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, recorderId)
    }
    catch (e: DeviceIdManager.InvalidDeviceIdTokenException) {
      EventLogConfiguration.LOG.warn("Failed retrieving device id for $recorderId")
      return UNDEFINED_DEVICE_ID
    }
  }

  private fun getOrGenerateSalt(): ByteArray {
    val app = ApplicationManager.getApplication()
    if (app != null && app.isHeadlessEnvironment) {
      val property = eventLogConfiguration.getHeadlessSaltProperty(recorderId)
      System.getProperty(property)?.let {
        return it.toByteArray(Charsets.UTF_8)
      }
    }

    return EventLogConfiguration.getOrGenerateSaltFromPrefs(recorderId)
  }

  /**
   * Returns the number of files that could be sent at once or -1 if there is no limit
   */
  internal fun getMaxFilesToSend(): Int {
    val app = ApplicationManager.getApplication()
    if (app != null && app.isHeadlessEnvironment) {
      val property = eventLogConfiguration.getHeadlessMaxFilesToSendProperty(recorderId)
      val value = System.getProperty(property)?.toIntOrNull()
      if (value != null && (value == -1 || value >= 0)) {
        return value
      }
    }
    return DEFAULT_MAX_FILES_TO_SEND
  }

  companion object {
    private const val DEFAULT_MAX_FILES_TO_SEND = 5
  }
}

private class AnonymizedIdsCache {
  private val cache: Cache<String, String> = Caffeine.newBuilder().maximumSize(200).build()

  fun computeIfAbsent(data: String, mappingFunction: (String) -> String): String {
    return cache.get(data, mappingFunction)
  }
}