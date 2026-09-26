// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata.validator

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.findPluginTypeByValue
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureUsageCustomValidatorsUtilTest {
  @Test
  fun `test plugin type detected by value`() {
    assertEquals(PluginType.NOT_LISTED, findPluginTypeByValue("NOT_LISTED"))
    assertEquals(PluginType.UNKNOWN, findPluginTypeByValue("UNKNOWN"))
    assertEquals(PluginType.LISTED, findPluginTypeByValue("LISTED"))
    assertEquals(PluginType.JB_NOT_BUNDLED, findPluginTypeByValue("JB_NOT_BUNDLED"))
    assertEquals(PluginType.JB_BUNDLED, findPluginTypeByValue("JB_BUNDLED"))
    assertEquals(PluginType.PLATFORM, findPluginTypeByValue("PLATFORM"))
    assertEquals(PluginType.JVM_CORE, findPluginTypeByValue("JVM_CORE"))
    assertEquals(PluginType.JB_UPDATED_BUNDLED, findPluginTypeByValue("JB_UPDATED_BUNDLED"))
    assertEquals(PluginType.JB_UPDATED_BUNDLED, findPluginTypeByValue("JB_UPDATED_BUNDLED"))
  }

  @Test
  fun `test rejected invalid plugin type`() {
    assertNull(findPluginTypeByValue(""))
    assertNull(findPluginTypeByValue("OTHER_VALUE"))
  }

  @Test
  fun `test reject action from unknown or not listed plugin`() {
    doRejectTest(newContext(null, null))
    doRejectTest(newContext("", null))
    doRejectTest(newContext("SOME_TYPE", null))
    doRejectTest(newContext("SOME_TYPE", "my.plugin.id"))
    doRejectTest(newContext("", "my.plugin.id"))
    doRejectTest(newContext(null, "my.plugin.id"))
    doRejectTest(newContext("UNKNOWN", "my.plugin.id"))
    doRejectTest(newContext("UNKNOWN", ""))
    doRejectTest(newContext("UNKNOWN", null))
    doRejectTest(newContext("NOT_LISTED", "my.plugin.id"))
    doRejectTest(newContext("NOT_LISTED", ""))
    doRejectTest(newContext("NOT_LISTED", null))
  }

  @Test
  fun `test actions from listed plugin`() {
    doAcceptTest(false, newContext("LISTED", "my.plugin.id"))

    doRejectTest(false, newContext("NOT_LISTED", ""))
    doRejectTest(false, newContext("NOT_LISTED", null))
  }

  @Test
  fun `test action from jb plugin`() {
    doRejectTest(true, newContext("LISTED", "my.plugin.id"))
    doRejectTest(true, newContext("LISTED", ""))
    doRejectTest(true, newContext("LISTED", null))
  }

  @Test
  fun `test action from platform or jb plugin`() {
    doAcceptTest(newContext("JB_NOT_BUNDLED", "my.plugin.id"))
    doAcceptTest(newContext("JB_BUNDLED", "my.plugin.id"))
    doAcceptTest(newContext("PLATFORM", "my.plugin.id"))
    doAcceptTest(newContext("PLATFORM", ""))
    doAcceptTest(newContext("PLATFORM", null))

    doRejectTest(newContext("JB_BUNDLED", ""))
    doRejectTest(newContext("JB_BUNDLED", null))
    doRejectTest(newContext("JB_NOT_BUNDLED", ""))
    doRejectTest(newContext("JB_NOT_BUNDLED", null))
  }

  private fun doAcceptTest(context: EventContext) {
    doAcceptTest(true, context)
    doAcceptTest(false, context)
  }

  private fun doAcceptTest(fromJBPlugin: Boolean, context: EventContext) {
    val rule = TestCheckPluginTypeCustomValidationRule(fromJBPlugin)
    assertEquals(ValidationResultType.ACCEPTED, rule.validate("data", context))
  }

  private fun doRejectTest(context: EventContext) {
    doRejectTest(true, context)
    doRejectTest(false, context)
  }

  private fun doRejectTest(fromJBPlugin: Boolean, context: EventContext) {
    val rule = TestCheckPluginTypeCustomValidationRule(fromJBPlugin)
    assertEquals(ValidationResultType.REJECTED, rule.validate("data", context))
  }
}

private fun newContext(plugin_type: String?, plugin: String?): EventContext {
  val data = HashMap<String, Any>()
  plugin?.let { data["plugin"] = plugin }
  plugin_type?.let { data["plugin_type"] = plugin_type }
  return EventContext.create("data", data)
}

class TestCheckPluginTypeCustomValidationRule(private val fromJBPlugin: Boolean) : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = true

  override fun doValidate(data: String, context: IEventContext): ValidationResultType =
    if (fromJBPlugin) acceptWhenReportedByJetBrainsPlugin(context) else acceptWhenReportedByPluginFromPluginRepository(context)
}
