// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.jetbrains.fus.reporting.model.lion3.LogEvent

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

private class EventDataValidator(val name: String, val value: Any, val condition: ((LogEvent) -> Boolean)? = null) : SimpleTestStatisticsEventValidator() {
  override fun validate(event: LogEvent) {
    if (condition == null || condition.invoke(event)) {
      val actual = event.event.data[name]
      assert(value == actual) { "Event data value is different from expected, expected: '$value', actual: $actual" }
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

  fun hasField(name: String, value: Any, condition: ((LogEvent) -> Boolean)? = null): TestStatisticsEventValidatorBuilder {
    validators.add(EventDataValidator(name, value, condition))
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