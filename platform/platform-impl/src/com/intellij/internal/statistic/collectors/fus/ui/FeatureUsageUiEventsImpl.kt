// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogWrapper

internal object DialogsCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ui.dialogs", 62)

  val EXIT_CODE: PrimitiveEventField<Int> = object: PrimitiveEventField<Int>() {
    override val name: String = "code"

    override val validationRule: List<String>
      get() = listOf("{enum:0|1|2}")

    override fun addData(fuData: FeatureUsageData, value: Int) {
      val toReport = getExitCodeToReport(value)
      fuData.addData(name, toReport)
    }

    private fun getExitCodeToReport(exitCode: Int): Int {
      if (exitCode == DialogWrapper.OK_EXIT_CODE || exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        return exitCode
      }
      return DialogWrapper.NEXT_USER_EXIT_CODE
    }
  }

  val DIALOG_CLASS: ClassEventField = EventFields.Class("dialog_class")
  val INVOCATION_PLACE: StringEventField = StringValidatedByCustomRule("dialog_invocation_place", ListValidationRule::class.java)

  val SHOW: VarargEventId = GROUP.registerVarargEvent("show", DIALOG_CLASS, INVOCATION_PLACE, EventFields.PluginInfo)
  val CLOSE: VarargEventId = GROUP.registerVarargEvent("close", DIALOG_CLASS, INVOCATION_PLACE, EXIT_CODE, EventFields.PluginInfo)
  val HELP: VarargEventId = GROUP.registerVarargEvent("help.clicked", DIALOG_CLASS, INVOCATION_PLACE, EventFields.PluginInfo)

  override fun getGroup(): EventLogGroup = GROUP
}

 /**This ensures that invocation places are collected only from internal plugins
 and plugins from the marketplace that are safe to report.
 This is done to prevent the collection of any potentially personal or sensitive information,
 as only explicitly-defined and safe invocation places are considered.*/
internal class ListValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "dialog_invocation_place"
  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val invocationPlaces = DialogInvocationPlacesCollector.getInstance().getInvocationPlaces()
    if (invocationPlaces.contains(data)) return ValidationResultType.ACCEPTED
    return ValidationResultType.REJECTED
  }
}

internal object SettingsCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ui.settings", 63)

  private val CONFIGURABLE_CLASS = EventFields.Class("configurable")
  val SELECT: EventId3<Class<*>?, Boolean, Long> = GROUP.registerEvent("select",
                                                                       CONFIGURABLE_CLASS,
                                                                       EventFields.Boolean("loaded_from_cache"),
                                                                       EventFields.DurationMs)
  val APPLY: EventId1<Class<*>?> = GROUP.registerEvent("apply", CONFIGURABLE_CLASS)
  val RESET: EventId1<Class<*>?> = GROUP.registerEvent("reset", CONFIGURABLE_CLASS)
  @JvmField
  val SEARCH: EventId3<Class<*>?, Int, Int> = GROUP.registerEvent("search", CONFIGURABLE_CLASS,
                                                                  EventFields.Int("hits"),
                                                                  EventFields.Int("characters"))
  val ADVANDED_SETTINGS_SEARCH: EventId3<Int, Int, Boolean> = GROUP.registerEvent("advanced.settings.search",
                                                                                  EventFields.Int("hits"),
                                                                                  EventFields.Int("characters"),
                                                                                  EventFields.Boolean("modifiedOnly"))

  override fun getGroup(): EventLogGroup = GROUP
}

internal class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable, loadedFromCache: Boolean, loadTimeMs: Long) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      val wrapper = configurable as? ConfigurableWrapper
      SettingsCounterUsagesCollector.SELECT.log(
        wrapper?.project,
        (wrapper?.configurable ?: configurable)::class.java,
        loadedFromCache,
        loadTimeMs
      )
    }
  }

  override fun logApplyConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.APPLY)
    }
  }

  override fun logResetConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.RESET)
    }
  }

  private fun logSettingsEvent(configurable: Configurable, event: EventId1<Class<*>>) {
    val wrapper = configurable as? ConfigurableWrapper
    event.log(wrapper?.project, (wrapper?.configurable ?: configurable)::class.java)
  }

  override fun logShowDialog(clazz: Class<*>, invocationPlace: String?) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      DialogsCounterUsagesCollector.SHOW.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
                                             DialogsCounterUsagesCollector.INVOCATION_PLACE.with(invocationPlace))
    }
  }

  override fun logCloseDialog(clazz: Class<*>, exitCode: Int, invocationPlace: String?) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      DialogsCounterUsagesCollector.CLOSE.log(
        DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
        DialogsCounterUsagesCollector.INVOCATION_PLACE.with(invocationPlace),
        DialogsCounterUsagesCollector.EXIT_CODE.with(exitCode)
      )
    }
  }

  override fun logClickOnHelpDialog(clazz: Class<*>, invocationPlace: String?) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      DialogsCounterUsagesCollector.HELP.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
                                             DialogsCounterUsagesCollector.INVOCATION_PLACE.with(invocationPlace))
    }
  }
}