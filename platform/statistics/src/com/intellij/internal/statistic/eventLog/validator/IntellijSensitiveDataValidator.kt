// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRuleFactory
import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RecorderDataValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.internal.statistic.eventLog.validator.storage.FusComponentProvider
import com.intellij.internal.statistic.eventLog.validator.storage.IntellijValidationRulesStorage
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.fus.reporting.MessageBus
import com.jetbrains.fus.reporting.MetadataStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * The data from all collectors is validated before it's recorded locally.
 * It's necessary to make sure that the data is correct and it doesn't contain personal or proprietary information.
 *
 * Validation is performed right before logging in [IntellijSensitiveDataValidator.validate].
 *
 * Therefore, each collector should define data scheme and rules which will be used in validation.
 * Rules are stored in a separate repository, IDE loads rules from the server during runtime.
 * If you use new FUS API (docs: fus-collectors.md) and group is implemented in a platform or bundled plugin,
 * synchronization between statistics metadata repository and source code is performed semi-automatically.
 * In other cases or when you need to change group scheme without changing the code, create an [issue](https://youtrack.jetbrains.com/issues/FUS).
 *
 * There are 3 types of rules:
 * 1. **Enum**: a list of possible values, e.g.
 *    `{enum:started|finished}` checks that the value is equal to 'started' or 'finished'.
 *    See: [EnumValidationRule]
 * 2. **Regexp**: e.g. `{regexp#integer}` checks that the value is integer.
 *    See: [RegexpValidationRule]
 * 3. **Custom rule**: class which inherits [CustomValidationRule] and validates dynamic data like action id or file type, e.g.
 *    `{util#class_name}` checks that the value is a class name from platform, JetBrains plugin or a plugin from JetBrains Marketplace.
 *    See: [com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator]
 *
 * There is also a list of common event data fields which doesn't require validation
 * because they are always validated in [FeatureUsageData], e.g. "plugin", "lang", etc.
 *
 * Example:
 * - "actions" collector records invoked actions
 *   ([com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl]).
 *   It is validated by the following rules:
 *   ```
 *   {
 *     "event_id" : [ "{enum:action.invoked|custom.action.invoked}" ],
 *     "event_data" : {
 *       "action_id" : [ "{util#action}" ],
 *       "class" : [ "{util#class_name}" ],
 *       "context_menu" : [ "{enum#boolean}" ],
 *       "current_file" : [ "{util#current_file}" ],
 *       "input_event" : [ "{util#shortcut}" ],
 *       "place" : [ "{util#place}" ],
 *       "plugin" : [ "{util#plugin}" ],
 *       "plugin_type" : [ "{util#plugin_type}" ]
 *     }
 *   }
 *   ```
 *
 * - "file.types" collector records information about project files
 *   ([com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector]).
 *   It is validated by the following rules:
 *   ```
 *   {
 *     "event_id" : ["{enum:file.type.in.project}" ],
 *     "event_data" : {
 *       "file_type" : [ "{util#file_type}" ]
 *     }
 *   }
 *   ```
 */
open class IntellijSensitiveDataValidator protected constructor(
  private val fusComponents: FusComponentProvider.FusComponents?,
  private val recorderId: String,
) : SensitiveDataValidator<MetadataStorage<EventLogBuild>>(fusComponents?.metadataStorage ?: EMPTY_METADATA_STORAGE) {
  companion object {
    private val instances = ConcurrentHashMap<String, IntellijSensitiveDataValidator>()

    init {
      CustomValidationRule.EP_NAME.addChangeListener({ instances.clear() }, null)
      CustomValidationRuleFactory.EP_NAME.addChangeListener({ instances.clear() }, null)
    }

    @JvmStatic
    fun clearInstances() {
      instances.clear()
    }

    @JvmStatic
    fun getInstance(recorderId: String): IntellijSensitiveDataValidator {
      return instances.computeIfAbsent(recorderId) { id ->
        if (ApplicationManager.getApplication().isUnitTestMode) {
          BlindSensitiveDataValidator(FusComponentProvider.createBlindFusComponents(id), id)
        }
        else {
          IntellijSensitiveDataValidator(FusComponentProvider.createFusComponents(id), id)
        }
      }
    }

    @JvmStatic
    fun getIfInitialized(recorderId: String): IntellijSensitiveDataValidator? {
      return instances[recorderId]
    }

    private val EMPTY_METADATA_STORAGE: MetadataStorage<EventLogBuild> = object : MetadataStorage<EventLogBuild> {
      override suspend fun update(scope: CoroutineScope): Job = Job()
      override fun update(): Boolean = false
      override fun reload() {}
      override fun getFieldsToAnonymize(s: String, s1: String): Set<String> = emptySet()
      override fun getSkipAnonymizationIds(): Set<String> = emptySet()
      override fun getGroupValidators(s: String): IGroupValidators<EventLogBuild> = object : IGroupValidators<EventLogBuild> {
        override val eventGroupRules: IEventGroupRules? = null
        override val versionFilter: IEventGroupsFilterRules<EventLogBuild>? = null
      }
      override fun isUnreachable(): Boolean = false
      override fun getSystemDataRulesRevisions(): RecorderDataValidationRule = throw NotImplementedError()
      override fun getClientDataRulesRevisions(): RecorderDataValidationRule = throw NotImplementedError()
      override fun getIdsRulesRevisions(): RecorderDataValidationRule = throw NotImplementedError()
    }
  }

  /**
   * @deprecated Do not use this. Metadata/dictionary storage is handled internally by ap-validation library.
   */
  @Deprecated("Do not use this. Metadata/dictionary storage is handled internally by ap-validation library.")
  @Suppress("UNUSED_PARAMETER")
  protected constructor(storage: IntellijValidationRulesStorage, recorderId: String) : this(null, recorderId)

  val messageBus: MessageBus
    get() = fusComponents!!.messageBus

  open fun isGroupAllowed(group: EventLogGroup): Boolean {
    if (StatisticsRecorderUtil.isTestModeEnabled(recorderId)) {
      return true
    }

    val storage = validationRulesStorage
    if (storage.isUnreachable()) {
      return true
    }
    return storage.getGroupValidators(group.id).eventGroupRules != null
  }

  /**
   * only for binary compatibility - in case somebody has overridden this method
   *
   * @deprecated If you really must, override the same methods using IEventContext and IEventGroupRules as parameters
   */
  @Deprecated("If you really must, override the same methods using IEventContext and IEventGroupRules as parameters", ReplaceWith("guaranteeCorrectEventId(context as IEventContext, groupRules as IEventGroupRules)"))
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.1")
  open fun guaranteeCorrectEventId(context: EventContext, groupRules: EventGroupRules?): String? {
    return null
  }

  override fun guaranteeCorrectEventId(context: IEventContext, groupRules: IEventGroupRules?): String {
    if (context is EventContext && groupRules is EventGroupRules?) {
      @Suppress("DEPRECATION")
      val result = guaranteeCorrectEventId(context, groupRules)
      if (result != null) {
        return result
      }
    }
    return super.guaranteeCorrectEventId(context, groupRules)
  }

  /**
   * only for binary compatibility - in case somebody has overridden this method
   *
   * @deprecated If you really must, override the same methods using IEventContext and IEventGroupRules as parameters
   */
  @Deprecated("If you really must, override the same methods using IEventContext and IEventGroupRules as parameters", ReplaceWith("guaranteeCorrectEventData(context as IEventContext, groupRules as IEventGroupRules)"))
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.1")
  open fun guaranteeCorrectEventData(context: EventContext, groupRules: EventGroupRules?): Map<String, Any>? {
    return null
  }

  override fun guaranteeCorrectEventData(context: IEventContext, groupRules: IEventGroupRules?): MutableMap<String, Any> {
    if (context is EventContext && groupRules is EventGroupRules?) {
      @Suppress("DEPRECATION")
      val result = guaranteeCorrectEventData(context, groupRules)
      if (result != null) {
        @Suppress("UNCHECKED_CAST")
        return result as MutableMap<String, Any>
      }
    }

    if (isTestModeEnabled(groupRules)) {
      @Suppress("UNCHECKED_CAST")
      return context.eventData as MutableMap<String, Any>
    }

    val validatedData = super.guaranteeCorrectEventData(context, groupRules)

    val containsPluginInfo = validatedData.containsKey("plugin") ||
                             validatedData.containsKey("plugin_type") ||
                             validatedData.containsKey("plugin_version")
    val pluginInfo = context.getPayload(CustomValidationRule.PLUGIN_INFO)
    if (pluginInfo != null && !containsPluginInfo) {
      StatisticsUtil.addPluginInfoTo(pluginInfo, validatedData)
    }
    return validatedData
  }

  private fun isTestModeEnabled(rule: IEventGroupRules?): Boolean {
    return StatisticsRecorderUtil.isTestModeEnabled(recorderId) && rule != null &&
           ContainerUtil.exists(rule.getEventIdRules()) { it is TestModeValidationRule }
  }

  fun update() {
    validationRulesStorage.update()
  }

  private class BlindSensitiveDataValidator(
    fusComponents: FusComponentProvider.FusComponents,
    recorderId: String,
  ) : IntellijSensitiveDataValidator(fusComponents, recorderId) {
    override fun guaranteeCorrectEventId(context: IEventContext, groupRules: IEventGroupRules?): String {
      return context.eventId
    }

    override fun guaranteeCorrectEventData(context: IEventContext, groupRules: IEventGroupRules?): MutableMap<String, Any> {
      @Suppress("UNCHECKED_CAST")
      return context.eventData as MutableMap<String, Any>
    }

    override fun isGroupAllowed(group: EventLogGroup): Boolean {
      return true
    }
  }
}