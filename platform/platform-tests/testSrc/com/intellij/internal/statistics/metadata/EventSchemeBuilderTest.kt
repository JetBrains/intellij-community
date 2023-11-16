// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder
import com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder.buildEventsScheme
import com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder.pluginInfoFields
import com.intellij.internal.statistic.eventLog.events.scheme.FieldDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.GroupDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.PluginSchemeDescriptor
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EventSchemeBuilderTest : BasePlatformTestCase() {

  fun `test generate string field validated by regexp`() {
    doFieldTest(EventFields.StringValidatedByRegexp("count", "integer"), hashSetOf("{regexp#integer}"))
  }

  fun `test generate string field validated by enum`() {
    doFieldTest(EventFields.StringValidatedByEnum("system", "os"), hashSetOf("{enum#os}"))
  }

  fun `test generate string field validated by custom rule`() {
    val customValidationRule = TestCustomValidationRule("custom_rule")
    CustomValidationRule.EP_NAME.point.registerExtension(customValidationRule, testRootDisposable)
    doFieldTest(EventFields.StringValidatedByCustomRule("class", TestCustomValidationRule::class.java), hashSetOf("{util#custom_rule}"))
  }

  fun `test generate string field validated by list of possible values`() {
    doFieldTest(EventFields.String("class", listOf("foo", "bar")), hashSetOf("{enum:foo|bar}"))
  }

  fun `test generate enum field`() {
    doFieldTest(EventFields.Enum("enum", TestEnum::class.java), hashSetOf("{enum:FOO|BAR}"))
  }

  fun `test generate string list validated by custom rule`() {
    val customValidationRule = TestCustomValidationRule("index_id")
    CustomValidationRule.EP_NAME.point.registerExtension(customValidationRule, testRootDisposable)
    doFieldTest(EventFields.StringValidatedByCustomRule("fields", TestCustomValidationRule::class.java), hashSetOf("{util#index_id}"))
  }

  fun `test generate string list validated by regexp`() {
    doFieldTest(EventFields.StringListValidatedByRegexp("fields", "index_id"), hashSetOf("{regexp#index_id}"))
  }

  fun `test generate string list validated by enum`() {
    doFieldTest(EventFields.StringListValidatedByEnum("fields", "index_id"), hashSetOf("{enum#index_id}"))
  }

  fun `test generate string list validated by list of possible values`() {
    doFieldTest(EventFields.StringList("fields", listOf("foo", "bar")), hashSetOf("{enum:foo|bar}"))
  }

  fun `test generate string validated by inline regexp`() {
    doFieldTest(EventFields.StringValidatedByInlineRegexp("id", "\\d+.\\d+"), hashSetOf("{regexp:\\d+.\\d+}"))
  }

  fun `test generate string list validated by inline regexp`() {
    doFieldTest(EventFields.StringListValidatedByInlineRegexp("fields", "\\d+.\\d+"), hashSetOf("{regexp:\\d+.\\d+}"))
  }

  fun `test generate plugin info with class name`() {
    val expectedValues = hashSetOf(FieldDescriptor("quickfix_name", hashSetOf("{util#class_name}"))) + pluginInfoFields
    doCompositeFieldTest(EventFields.Class("quickfix_name"), expectedValues)
  }

  fun `test generate plugin section`() {
    val descriptors = buildEventsScheme(null)
    assertTrue(descriptors.any { x -> x.plugin.id == "com.intellij" })
  }

  private fun doFieldTest(eventField: EventField<*>, expectedValues: Set<String>) {
    val group = buildGroupDescription(eventField)
    val event = group.schema.first()
    assertSameElements(event.fields.first().value, expectedValues)
  }

  private fun doCompositeFieldTest(eventField: EventField<*>, expectedValues: Set<FieldDescriptor>) {
    val group = buildGroupDescription(eventField)
    val event = group.schema.first()
    assertSameElements(event.fields, expectedValues)
  }

  private fun buildGroupDescription(eventField: EventField<*>): GroupDescriptor {
    val eventLogGroup = EventLogGroup("test.group.id", 1)
    eventLogGroup.registerEvent("test_event", eventField)
    val collector = EventsSchemeBuilder.FeatureUsageCollectorInfo(TestCounterCollector(eventLogGroup), PluginSchemeDescriptor("testPlugin"))
    val groups = EventsSchemeBuilder.collectGroupsFromExtensions("count", listOf(collector), "FUS")
    assertSize(1, groups)
    return groups.first()
  }

  enum class TestEnum { FOO, BAR }

  @Suppress("StatisticsCollectorNotRegistered")
  class TestCounterCollector(private val eventLogGroup: EventLogGroup) : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = eventLogGroup
  }

  class TestCustomValidationRule(private val ruleId: String) : CustomValidationRule() {
    override fun getRuleId(): String = ruleId

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      return ValidationResultType.ACCEPTED
    }
  }
}