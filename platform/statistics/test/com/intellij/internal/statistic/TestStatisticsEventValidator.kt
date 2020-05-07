// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.LogEvent

interface TestStatisticsEventsValidator {
  fun validateAll(events: List<LogEvent>)
}

abstract class SimpleTestStatisticsEventValidator : TestStatisticsEventsValidator {
  override fun validateAll(events: List<LogEvent>) {
    for (event in events) {
      validate(event)
    }
  }

  abstract fun validate(event: LogEvent)
}

private class EventDataExistsValidator(vararg fields: String) : SimpleTestStatisticsEventValidator() {
  val expectedFields: Array<out String> = fields

  override fun validate(event: LogEvent) {
    for (field in expectedFields) {
      assert(event.event.data.containsKey(field)) { "Cannot find '$field' field in event: ${event.event.data}" }
    }
  }
}

private class EventDataNotExistsValidator(vararg fields: String) : SimpleTestStatisticsEventValidator() {
  val expectedFields: Array<out String> = fields

  override fun validate(event: LogEvent) {
    for (field in expectedFields) {
      assert(!event.event.data.containsKey(field)) { "Field '$field' exists in event but should not: ${event.event.data}" }
    }
  }
}

private class EventIdValidator(val eventId: String) : SimpleTestStatisticsEventValidator() {
  override fun validate(event: LogEvent) {
    val actual = event.event.id
    assert(eventId == actual) { "Event id is different from expected, expected: '$eventId', actual: $actual" }
  }
}

private class CompositeEventValidator(val validators: List<TestStatisticsEventsValidator>) : TestStatisticsEventsValidator {
  override fun validateAll(events: List<LogEvent>) {
    for (validator in validators) {
      validator.validateAll(events)
    }
  }
}

class TestStatisticsEventValidatorBuilder {
  private val validators: MutableList<TestStatisticsEventsValidator> = arrayListOf()

  fun contains(vararg fields: String): TestStatisticsEventValidatorBuilder {
    validators.add(EventDataExistsValidator(*fields))
    return this
  }

  fun notContains(vararg fields: String): TestStatisticsEventValidatorBuilder {
    validators.add(EventDataNotExistsValidator(*fields))
    return this
  }

  fun hasEventId(eventId: String): TestStatisticsEventValidatorBuilder {
    validators.add(EventIdValidator(eventId))
    return this
  }

  fun withCustom(validator: TestStatisticsEventsValidator): TestStatisticsEventValidatorBuilder {
    validators.add(validator)
    return this
  }

  fun build(): TestStatisticsEventsValidator {
    return CompositeEventValidator(validators)
  }
}