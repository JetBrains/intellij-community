// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import junit.framework.TestCase
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.OutputStream
import java.io.PrintStream

// The tests in this file intentionally redirect and close System.out and System.err,
// to verify that UsefulTestCase.preserveGlobalState works as intended.
//
// UsefulTestCase restores System.out and System.err in case they were redirected.
//
// UsefulTestCase does not prevent System.out and System.err from being closed, though.
// After closing these streams, subsequent tests may fail.

// Protected by GlobalState and UsefulTestCase.
// Except for JUnit 5 tests, as these don't run the setUp/tearDown fixture.
private const val actuallyRedirect = false

// Only activate for manual testing.
private const val actuallyClose = false

// Plain JUnit 3 tests do not detect when System.our or System.err are redirected.
// The next UsefulTestCase or the _LastInSuiteTest will notice the ongoing redirection and complain.
class PlainJUnit3Test : TestCase() {
  fun testCloseOut() = doCloseOut()
  fun testCloseErr() = doCloseErr()
  fun testRedirectOut() = doRedirectOut()
  fun testRedirectErr() = doRedirectErr()
}

// JUnit 3 tests that derive from UsefulTestCase protect themselves by checking the system
// streams during setUp as well as during tearDown.
class UsefulJUnit3Test : UsefulTestCase() {
  fun testCloseOut() = doCloseOut()
  fun testCloseErr() = doCloseErr()
  fun testRedirectOut() = doRedirectOut()
  fun testRedirectErr() = doRedirectErr()
}

// JUnit 3 tests that derive from HeavyPlatformTestCase protect themselves
// just like tests derived from UsefulTestCase.
class HeavyJUnit3Test : HeavyPlatformTestCase() {
  fun testCloseOut() = doCloseOut()
  fun testCloseErr() = doCloseErr()
  fun testRedirectOut() = doRedirectOut()
  fun testRedirectErr() = doRedirectErr()
}

// JUnit 4 tests that derive from UsefulTestCase protect themselves,
// just like their JUnit 3 counterparts.
@RunWith(JUnit4::class)
class UsefulJUnit4Test : UsefulTestCase() {
  @org.junit.Test
  fun closeOut() = doCloseOut()

  @org.junit.Test
  fun closeErr() = doCloseErr()

  @org.junit.Test
  fun redirectOut() = doRedirectOut()

  @org.junit.Test
  fun redirectErr() = doRedirectErr()
}

// JUnit 4 tests that derive from HeavyPlatformTestCase protect themselves,
// just like their JUnit 3 counterparts.
@RunWith(JUnit4::class)
class HeavyJUnit4Test : HeavyPlatformTestCase() {
  @org.junit.Test
  fun closeOut() = doCloseOut()

  @org.junit.Test
  fun closeErr() = doCloseErr()

  @org.junit.Test
  fun redirectOut() = doRedirectOut()

  @org.junit.Test
  fun redirectErr() = doRedirectErr()
}

// !!!
// JUnit 5 tests that derive from UsefulTestCase are not supported.
// Their test methods are run without the setUp/tearDown fixture.
// !!!
class UsefulJUnit5Test : UsefulTestCase() {
  @org.junit.jupiter.api.Test
  fun closeOut() = doCloseOut()

  @org.junit.jupiter.api.Test
  fun closeErr() = doCloseErr()

  @org.junit.jupiter.api.Test
  fun redirectOut() = doRedirectOut()

  @org.junit.jupiter.api.Test
  fun redirectErr() = doRedirectErr()
}

// !!!
// JUnit 5 tests that derive (indirectly) from UsefulTestCase are not supported.
// Their test methods are run without the setUp/tearDown fixture.
// !!!
class HeavyJUnit5Test : HeavyPlatformTestCase() {
  @org.junit.jupiter.api.Test
  fun closeOut() = doCloseOut()

  @org.junit.jupiter.api.Test
  fun closeErr() = doCloseErr()

  @org.junit.jupiter.api.Test
  fun redirectOut() = doRedirectOut()

  @org.junit.jupiter.api.Test
  fun redirectErr() = doRedirectErr()
}

private fun doCloseOut() {
  if (!actuallyClose) return

  // When running the tests locally in the IDE, System.out is used to report the test status to the IDE.
  // Closing it stops that and finally fails the tests with exit status 255.
  System.out.close()

  // Closing the stream does not yet mark it as erroneous, only the next writing operation does.
  System.out.flush()
}

private fun doCloseErr() {
  if (!actuallyClose) return

  System.err.close()

  // Closing the stream does not yet mark it as erroneous, only the next writing operation does.
  System.err.flush()
}

private fun doRedirectOut() {
  if (!actuallyRedirect) return

  System.setOut(PrintStream(OutputStream.nullOutputStream()))
}

private fun doRedirectErr() {
  if (!actuallyRedirect) return

  System.setErr(PrintStream(OutputStream.nullOutputStream()))
}
