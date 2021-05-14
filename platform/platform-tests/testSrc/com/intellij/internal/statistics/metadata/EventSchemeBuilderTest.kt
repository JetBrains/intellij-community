// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventsSchemeBuilder
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
    doFieldTest(EventFields.StringValidatedByCustomRule("class", "class_name"), hashSetOf("{util#class_name}"))
  }

  fun `test generate string field validated by list of possible values`() {
    doFieldTest(EventFields.String("class", listOf("foo", "bar")), hashSetOf("{enum:foo|bar}"))
  }

  fun `test generate enum field`() {
    doFieldTest(EventFields.Enum("enum", TestEnum::class.java), hashSetOf("{enum:FOO|BAR}"))
  }

  fun `test generate string list validated by custom rule`() {
    doFieldTest(EventFields.StringValidatedByCustomRule("fields", "index_id"), hashSetOf("{util#index_id}"))
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

  private fun doFieldTest(eventField: EventField<*>, expectedValues: Set<String>) {
    val eventLogGroup = EventLogGroup("test.group.id", 1)
    eventLogGroup.registerEvent("test_event", eventField)
    val groups = EventsSchemeBuilder.collectGroupsFromExtensions("count", listOf(TestCounterCollector(eventLogGroup)))
    assertSize(1, groups)
    val group = groups.first()
    assertNotNull(group)
    val event = group.schema.first()
    assertSameElements(event.fields.first().value, expectedValues)
  }

  enum class TestEnum { FOO, BAR }

  @Suppress("StatisticsCollectorNotRegistered")
  class TestCounterCollector(val eventLogGroup: EventLogGroup) : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = eventLogGroup
  }
}