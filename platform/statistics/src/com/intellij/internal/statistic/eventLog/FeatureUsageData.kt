// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.codeWithMe.ClientId
import com.intellij.internal.statistic.collectors.fus.ActionPlaceHolder
import com.intellij.internal.statistic.eventLog.StatisticsEventEscaper.escapeFieldName
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import java.util.regex.Pattern

private val LOG = logger<FeatureUsageData>()

/**
 * <p>FeatureUsageData represents additional data for reported event.</p>
 *
 * <h3>Example</h3>
 *
 * <p>My usage collector collects actions invocations. <i>"my.foo.action"</i> could be invoked from one of the following contexts:
 * "main.menu", "context.menu", "my.dialog", "all-actions-run".</p>
 *
 * <p>If I write {@code FUCounterUsageLogger.logEvent("my.foo.action", "bar")}, I'll know how many times the action "bar" was invoked (e.g. 239)</p>
 *
 * <p>If I write {@code FUCounterUsageLogger.logEvent("my.foo.action", "bar", new FeatureUsageData().addPlace(place))}, I'll get the same
 * total count of action invocations (239), but I'll also know that the action was called 3 times from "main.menu", 235 times from "my.dialog" and only once from "context.menu".
 * <br/>
 * </p>
 */
@ApiStatus.Internal
class FeatureUsageData(val recorderId: String) {
  @TestOnly
  @Deprecated("Recorder ID should be explicitly provided", replaceWith = ReplaceWith("FeatureUsageData(recorderId)"), DeprecationLevel.WARNING)
  constructor() : this("FUS")

  private var data: MutableMap<String, Any> = HashMap()

  init {
    val clientId = ClientId.currentOrNull
    if (clientId != null && clientId != ClientId.defaultLocalId) {
      addClientId(clientId.value)
    }
    if (QODANA_EVENTS_DATA.projectId != null) {
      data["system_qdcld_project_id"] = QODANA_EVENTS_DATA.projectId
    }
    if (QODANA_EVENTS_DATA.organizationId != null) {
      data["system_qdcld_org_id"] = QODANA_EVENTS_DATA.organizationId
    }
  }

  companion object {
    // don't list "version" as "platformDataKeys" because it's format depends a lot on the tool
    val platformDataKeys: List<String> = listOf("plugin", "project", "os", "plugin_type", "lang", "current_file", "input_event", "place",
                                                "file_path", "anonymous_id", "client_id", "system_qdcld_project_id", "system_qdcld_org_id")

    private val QODANA_EVENTS_DATA: QodanaEventsData = calcQodanaEventsData()
  }

  fun addClientId(clientId: String?): FeatureUsageData {
    clientId?.let {
      val permanentClientId = parsePermanentClientId(clientId)
      data["client_id"] = EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(permanentClientId)
    }
    return this
  }

  private fun parsePermanentClientId(clientId: String): String {
    val separator = clientId.indexOf('-')
    if (separator > 0) {
      return clientId.substring(0, separator)
    }
    return clientId
  }

  /**
   * Project data is added automatically for project state collectors and project-wide counter events.
   *
   * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
   * @see com.intellij.internal.statistic.eventLog.events.EventId.log(Project)
   */
  fun addProject(project: Project?): FeatureUsageData {
    if (project != null) {
      data["project"] = EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(project.getProjectCacheFileName())
    }
    return this
  }

  fun addVersionByString(@NonNls version: String?): FeatureUsageData {
    if (version == null) {
      data["version"] = "unknown"
    }
    else {
      addVersion(Version.parseVersion(version))
    }
    return this
  }

  fun addVersion(@NonNls version: Version?): FeatureUsageData {
    data["version"] = if (version != null) "${version.major}.${version.minor}" else "unknown.format"
    return this
  }

  fun addPluginInfo(info: PluginInfo?): FeatureUsageData {
    info?.let {
      StatisticsUtil.addPluginInfoTo(info, data)
    }
    return this
  }

  fun addLanguage(@NonNls id: String?): FeatureUsageData {
    id?.let {
      addLanguage(Language.findLanguageByID(id))
    }
    return this
  }

  fun addLanguage(language: Language?): FeatureUsageData {
    return addLanguageInternal("lang", language)
  }

  fun addLanguage(name: String, language: Language?): FeatureUsageData {
    return addLanguageInternal(name, language)
  }

  fun addCurrentFile(language: Language?): FeatureUsageData {
    return addLanguageInternal("current_file", language)
  }

  private fun addLanguageInternal(fieldName: String, language: Language?): FeatureUsageData {
    language?.let {
      val type = getPluginInfo(language.javaClass)
      if (type.isSafeToReport()) {
        data[fieldName] = language.id
      }
      else {
        data[fieldName] = "third.party"
      }
    }
    return this
  }

  fun addInputEvent(event: InputEvent?, @NonNls place: String?): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getInputEventText(event, place)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addInputEvent(event: AnActionEvent?): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getActionEventText(event)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addInputEvent(event: KeyEvent): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getKeyEventText(event)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addInputEvent(event: MouseEvent): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getMouseEventText(event)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addPlace(@NonNls place: String?): FeatureUsageData {
    if (place == null) return this

    var reported = ActionPlaces.UNKNOWN
    if (isCommonPlace(place) || ActionPlaceHolder.isCustomActionPlace(place)) {
      reported = place
    }
    else if (ActionPlaces.isPopupPlace(place)) {
      reported = ActionPlaces.POPUP
    }
    data["place"] = reported
    return this
  }

  private fun isCommonPlace(place: String): Boolean {
    return ActionPlaces.isCommonPlace(place)
  }

  fun addAnonymizedPath(@NonNls path: String?): FeatureUsageData {
    data["file_path"] = path?.let { EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(path) } ?: "undefined"
    return this
  }

  fun addAnonymizedId(@NonNls id: String): FeatureUsageData {
    data["anonymous_id"] = EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(id)
    return this
  }

  // Added for java compatibility
  fun addAnonymizedValue(@NonNls key: String, @NonNls value: String?): FeatureUsageData {
    addAnonymizedValue(key, value, false)
    return this
  }

  fun addAnonymizedValue(@NonNls key: String, @NonNls value: List<String>): FeatureUsageData {
    data[key] = value.map { EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(it, false) }
    return this
  }

  fun addAnonymizedValue(@NonNls key: String, @NonNls value: String?, short: Boolean = false): FeatureUsageData {
    data[key] = value?.let { EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(value, short) } ?: "undefined"
    return this
  }

  fun addDatedShortAnonymizedValue(@NonNls key: String, timestamp: Long, @NonNls value: String?): FeatureUsageData {
    data[key] =
      "${StatisticsUtil.getTimestampDateInUTC(timestamp)}-" +
      (value?.let { EventLogConfiguration.getInstance().getOrCreate(recorderId).anonymize(value, true) } ?: "undefined")
    return this
  }

  fun addValue(value: Any): FeatureUsageData {
    if (value is String || value is Boolean || value is Int || value is Long || value is Float || value is Double) {
      return addDataInternal("value", value)
    }
    return addData("value", value.toString())
  }

  fun addEnabled(enabled: Boolean): FeatureUsageData {
    return addData("enabled", enabled)
  }

  fun addCount(count: Int): FeatureUsageData {
    return addData("count", count)
  }

  /**
   * @param key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, value: Boolean): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * @param key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, value: Int): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * @param key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, value: Long): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * @param key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, value: Float): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * @param key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, value: Double): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * @param key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   * @param value can contain "-", "_", ".", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, @NonNls value: String): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * The data reported by this method will be available ONLY for ad-hoc analysis.
   *
   * @param key key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  fun addData(@NonNls key: String, value: List<String>): FeatureUsageData {
    return addDataInternal(key, value)
  }

  /**
   * The data reported by this method will be available ONLY for ad-hoc analysis.
   *
   * @param key key can contain "-", "_", latin letters or digits. All not allowed symbols will be replaced with "_" or "?".
   */
  internal fun addListNumberData(@NonNls key: String, value: List<Number>): FeatureUsageData {
    return addDataInternal(key, value)
  }

  internal fun addListLongData(@NonNls key: String, value: List<Long>): FeatureUsageData {
    return addListNumberData(key, value)
  }

  internal fun addObjectData(@NonNls key: String, value: Map<String, Any>): FeatureUsageData {
    return addDataInternal(key, value)
  }

  internal fun addListObjectData(@NonNls key: String, value: List<Map<String, Any>>): FeatureUsageData {
    return addDataInternal(key, value)
  }

  private fun addDataInternal(key: String, value: Any): FeatureUsageData {
    if (!ApplicationManager.getApplication().isUnitTestMode && platformDataKeys.contains(key)) {
      LOG.warn("Collectors should not reuse platform keys: $key")
      return this
    }

    val escapedKey = escapeFieldName(key)
    if (escapedKey != key) {
      LOG.warn("Key contains invalid symbols, they will be escaped: '$key' -> '$escapedKey'")
    }
    data[escapedKey] = value
    return this
  }

  fun build(): Map<String, Any> {
    if (data.isEmpty()) {
      return Collections.emptyMap()
    }
    return data
  }

  fun addAll(from: FeatureUsageData) : FeatureUsageData{
    data.putAll(from.data)
    return this
  }

  fun merge(next: FeatureUsageData, @NonNls prefix: String): FeatureUsageData {
    for ((key, value) in next.build()) {
      val newKey = if (key.startsWith("data_")) "$prefix$key" else key
      data[newKey] = value
    }
    return this
  }

  fun copy(): FeatureUsageData {
    val result = FeatureUsageData(recorderId)
    for ((key, value) in data) {
      result.data[key] = value
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FeatureUsageData

    return data == other.data
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }

  override fun toString(): String {
    return data.toString()
  }
}