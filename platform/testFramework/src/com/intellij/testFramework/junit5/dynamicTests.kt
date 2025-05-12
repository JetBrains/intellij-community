// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DynamicTests")
package com.intellij.testFramework.junit5

import org.jetbrains.annotations.ApiStatus
import org.junit.AssumptionViolatedException
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.function.Executable
import org.opentest4j.AssertionFailedError
import org.opentest4j.IncompleteExecutionException
import org.opentest4j.MultipleFailuresError
import org.opentest4j.TestAbortedException

/**
 * Describes a test failure. [name] will be used as a test name for a separate test failure when [asDynamicTests] is called.
 */
data class NamedFailure(val name: String, val error: Throwable) {
  constructor(name: String, errorMessage: String) : this(name, AssertionFailedError(errorMessage))
}

/**
 * Groups multiple exception using presentable names obtained via [naming] function.
 */
fun <T : Throwable> Collection<T>.groupFailures(naming: (T) -> String): List<NamedFailure> =
  groupBy { naming(it) }.map { (name, errors) ->
    NamedFailure(name, errors.singleOrNull() ?: MultipleFailuresError("${errors.size} failures", errors))
  }

/**
 * Converts multiple failures to separate tests.
 * If there are no failures in the list, a single (successful) test with name `"no $problemMessage"` will be created.
 * If there are more than [threshold] failures, a single test with name `"too many $problemMessage"` accumulating all the failures
 * will be reported, assuming that failures are caused by the same reason, and it's better not to report them separately.
 * Otherwise, all failures are reported as separate (dynamic) tests, allowing us to track and investigate different failures separately.
 *
 * In order to use this, you need to write a test class with JUnit5 and return results of this method from a method annotated with [org.junit.jupiter.api.TestFactory].
 */
@JvmOverloads
fun List<NamedFailure>.asDynamicTests(problemMessage: String, threshold: Int = 50): List<DynamicTest> {
  @Suppress("DEPRECATION")
  return asDynamicTests("no $problemMessage", "too many $problemMessage", threshold)
}

@Deprecated("Use a simplified version 'asDynamicTests(String, Int)' instead")
@ApiStatus.ScheduledForRemoval
@JvmOverloads
fun List<NamedFailure>.asDynamicTests(testNameForSuccess: String, testNameForManyFailures: String, threshold: Int = 50): List<DynamicTest> {
  if (isEmpty()) {
    return listOf(DynamicTest.dynamicTest(testNameForSuccess) {})
  }
  else if (size <= threshold) {
    return map {
      DynamicTest.dynamicTest(it.name, Executable { throw it.error })
    }
  }
  else {
    //this may indicate a problem in the testing code, so it's better to report one failed test with all the errors inside
    return listOf(DynamicTest.dynamicTest(testNameForManyFailures) {
      if (all { it.error is AssumptionViolatedException || it.error is IncompleteExecutionException }) {
        val aborted = TestAbortedException("$testNameForManyFailures:\n${joinToString("\n") { it.error.toString() }}")
        forEach { 
          aborted.addSuppressed(it.error)
        }
        throw aborted
      }
      else {
        assertAll(map {
          {
            throw it.error
          }
        })
      }
    })
  }
}
