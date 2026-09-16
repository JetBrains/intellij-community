package com.intellij.execution.multilaunch.statistics

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.multilaunch.execution.executables.TaskExecutableTemplate
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("UnstableApiUsage")
class ExecutableTypeIdValidationRule : CustomValidationRule() {
  override fun getRuleId() = "multirun_executable_type_id"
  override fun acceptRuleId(ruleId: String?) = ruleId == getRuleId()
  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val validTypes = buildMap {
      putAll(ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.associate { it.id to it.javaClass })
      putAll(TaskExecutableTemplate.EP_NAME.extensionList.associate { it.type to it.javaClass })
    }
    val validType = validTypes[data] ?: return ValidationResultType.REJECTED
    return if (getPluginInfo(validType).isSafeToReport())
      ValidationResultType.ACCEPTED
    else
      ValidationResultType.THIRD_PARTY
  }
}