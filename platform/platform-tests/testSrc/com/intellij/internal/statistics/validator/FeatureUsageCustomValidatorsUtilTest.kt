// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.validator

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.findPluginTypeByValue
import org.junit.Assert
import org.junit.Test

class FeatureUsageCustomValidatorsUtilTest {

  @Test
  fun `test plugin type detected by value`() {
    Assert.assertEquals(PluginType.NOT_LISTED, findPluginTypeByValue("NOT_LISTED"))
    Assert.assertEquals(PluginType.UNKNOWN, findPluginTypeByValue("UNKNOWN"))
    Assert.assertEquals(PluginType.LISTED, findPluginTypeByValue("LISTED"))
    Assert.assertEquals(PluginType.JB_NOT_BUNDLED, findPluginTypeByValue("JB_NOT_BUNDLED"))
    Assert.assertEquals(PluginType.JB_BUNDLED, findPluginTypeByValue("JB_BUNDLED"))
    Assert.assertEquals(PluginType.PLATFORM, findPluginTypeByValue("PLATFORM"))
  }

  @Test
  fun `test rejected invalid plugin type`() {
    Assert.assertNull(findPluginTypeByValue(""))
    Assert.assertNull(findPluginTypeByValue("OTHER_VALUE"))
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
    val rule = TestCheckPluginTypeCustomWhiteListRule(fromJBPlugin)
    Assert.assertEquals(ValidationResultType.ACCEPTED, rule.validate("data", context))
  }

  private fun doRejectTest(context: EventContext) {
    doRejectTest(true, context)
    doRejectTest(false, context)
  }

  private fun doRejectTest(fromJBPlugin: Boolean, context: EventContext) {
    val rule = TestCheckPluginTypeCustomWhiteListRule(fromJBPlugin)
    Assert.assertEquals(ValidationResultType.REJECTED, rule.validate("data", context))
  }
}

private fun newContext(plugin_type: String?, plugin: String?): EventContext {
  val data = HashMap<String, Any>()
  plugin?.let { data["plugin"] = plugin }
  plugin_type?.let { data["plugin_type"] = plugin_type }
  return EventContext.create("data", data)
}

class TestCheckPluginTypeCustomWhiteListRule(private val fromJBPlugin: Boolean) : CustomWhiteListRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = true

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (fromJBPlugin) acceptWhenReportedByJetbrainsPlugin(context) else acceptWhenReportedByPluginFromPluginRepository(context)
  }
}