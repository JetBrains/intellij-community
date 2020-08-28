// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistBuilder
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
    doFieldTest(EventFields.String("class", listOf("foo", "bar")), hashSetOf("foo", "bar"))
  }

  fun `test generate enum field`() {
    doFieldTest(EventFields.Enum("enum", TestEnum::class.java), hashSetOf("FOO", "BAR"))
  }

  fun `test generate string list`() {
    doFieldTest(EventFields.StringList("fields").withCustomRule("index_id"), hashSetOf("{util#index_id}"))
  }

  private fun doFieldTest(eventField: EventField<*>, values: Set<String>) {
    val eventLogGroup = EventLogGroup("test.group.id", 1)
    eventLogGroup.registerEvent("test_event", eventField)
    val groups = WhitelistBuilder.collectWhitelistFromExtensions("count", listOf(TestCounterCollector(eventLogGroup)))
    assertSize(1, groups)
    val group = groups.first()
    assertNotNull(group)
    val event = group.schema.first()
    assertSameElements(event.fields.first().value, values)
  }

  enum class TestEnum { FOO, BAR }

  class TestCounterCollector(val eventLogGroup: EventLogGroup) : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = eventLogGroup
  }
}