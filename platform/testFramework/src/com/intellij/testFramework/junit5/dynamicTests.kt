// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DynamicTests")
package com.intellij.testFramework.junit5

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.function.Executable
import org.opentest4j.AssertionFailedError

/**
 * Describes a test failure. [name] will be used as a test name for a separate test failure when [asDynamicTests] is called.
 */
data class NamedFailure(val name: String, val error: Throwable) {
  constructor(name: String, errorMessage: String) : this(name, AssertionFailedError(errorMessage))
}

/**
 * Converts multiple failures to separate tests. If there are no failures in the list, a single (successful) test with name [testNameForSuccess]
 * will be created. If there are more than [threshold] failures, a single test with name [testNameForManyFailures] accumulating all the failures 
 * will be reported, assuming that failures are caused by the same reason, and it's better not to report them separately. Otherwise,
 * all failures are reported as separate (dynamic) test, allowing to track and investigate different failures separately.
 * 
 * In order to use this, you need to write a test class with JUnit5 and return results of this method from a method annotated with [org.junit.jupiter.api.TestFactory].
 */
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
      assertAll(map {
        {
          throw it.error
        }
      })
    })
  }
}
