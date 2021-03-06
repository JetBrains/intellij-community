// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.internal.statistic.*
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase

internal abstract class TestFileCandidatesValidator : TestStatisticsEventsValidator {
  override fun validateAll(events: List<LogEvent>) {
    CodeInsightFixtureTestCase.assertTrue("Number of events is not 1", events.size == 1)
    val candidates = events
      .filter { it.event.data.containsKey("candidates") }
      .map { it.event.data["candidates"] }
      .first()

    @Suppress("UNCHECKED_CAST")
    validateCandidates(candidates as List<Map<String, Any>>)
  }

  abstract fun validateCandidates(candidates: List<Map<String, Any>>)
}

private class FileCandidatesSizeValidators(val size: Int) : TestFileCandidatesValidator() {
  override fun validateCandidates(candidates: List<Map<String, Any>>) {
    val actual = candidates.size
    assert(actual == size) { "Number of candidates is different from expected, expected: '$size', actual: $actual" }
  }
}

internal abstract class SimpleCandidateValidator : TestFileCandidatesValidator() {

  override fun validateCandidates(candidates: List<Map<String, Any>>) {
    for (candidate in candidates) {
      validate(candidate)
    }
  }

  abstract fun validate(candidateFields: Map<String, Any>)
}

private class EventDataExistsValidator(vararg fields: String) : SimpleCandidateValidator() {
  val expectedFields: Array<out String> = fields

  override fun validate(candidateFields: Map<String, Any>) {
    for (field in expectedFields) {
      assert(candidateFields.containsKey(field)) { "Cannot find '$field' field in event: ${candidateFields.keys}" }
    }
  }
}

private class EventDataNotExistsValidator(vararg fields: String) : SimpleCandidateValidator() {
  val expectedFields: Array<out String> = fields

  override fun validate(candidateFields: Map<String, Any>) {
    for (field in expectedFields) {
      assert(!candidateFields.containsKey(field)) { "Field '$field' exists in event but should not: ${candidateFields.keys}" }
    }
  }
}

private class EventDataValidator(val name: String,
                                 val value: Any,
                                 val condition: ((Map<String, Any>) -> Boolean)? = null) : SimpleCandidateValidator() {
  override fun validate(candidateFields: Map<String, Any>) {
    if (condition == null || condition.invoke(candidateFields)) {
      val actual = candidateFields[name]
      assert(value == actual) { "Event data value is different from expected, expected: '$value', actual: $actual" }
    }
  }
}

private class CompositeCandidateValidator(val validators: List<TestFileCandidatesValidator>) : TestFileCandidatesValidator() {
  override fun validateCandidates(candidates: List<Map<String, Any>>) {
    for (validator in validators) {
      validator.validateCandidates(candidates)
    }
  }
}

internal class TestFileCandidatesValidatorBuilder {
  private val validators: MutableList<TestFileCandidatesValidator> = arrayListOf()

  fun withCandidateSize(size: Int): TestFileCandidatesValidatorBuilder {
    validators.add(FileCandidatesSizeValidators(size))
    return this
  }

  fun contains(vararg fields: String): TestFileCandidatesValidatorBuilder {
    validators.add(EventDataExistsValidator(*fields))
    return this
  }

  fun notContains(vararg fields: String): TestFileCandidatesValidatorBuilder {
    validators.add(EventDataNotExistsValidator(*fields))
    return this
  }

  fun hasField(name: String, value: Any, condition: ((Map<String, Any>) -> Boolean)? = null): TestFileCandidatesValidatorBuilder {
    validators.add(EventDataValidator(name, value, condition))
    return this
  }

  fun withCustom(validator: TestFileCandidatesValidator): TestFileCandidatesValidatorBuilder {
    validators.add(validator)
    return this
  }

  fun build(): TestFileCandidatesValidator {
    return CompositeCandidateValidator(validators)
  }
}