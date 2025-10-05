// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.vfs

import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthChecker
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthChecker.VFSHealthCheckReport
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

private const val LOCAL_STORE_REPORT_KEY = "vfs-health-report-key"

/**
 * Checks VFS to be consistent before|after all|each test.
 * Use [com.intellij.testFramework.utils.vfs.CheckVFSHealthRule] for junit4 tests
 */
@TestOnly
class CheckVFSHealthExtension
private constructor(private val checkBeforeEach: Boolean = false,
                    private val checkAfterEach: Boolean = false,
                    private val checkBeforeAll: Boolean = false,
                    private val checkAfterAll: Boolean = false,
                    private val checkOnlyNewErrors: Boolean = true) : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {


  /**
   * Default ctor, used for annotation-based extension assignment.
   * Checks for new errors, once for the class
   */
  @Suppress("unused")
  constructor() : this(checkBeforeEach = false, checkAfterEach = false,
                       checkBeforeAll = true, checkAfterAll = true,
                       checkOnlyNewErrors = true)

  init {
    if (!checkBeforeAll && !checkBeforeEach && !checkAfterAll && !checkAfterEach) {
      throw IllegalArgumentException(
        "All check[Before|After][All|Each] flags are false -- nothing to do. At least one of flags must be true")
    }
    if (checkOnlyNewErrors) {
      if (!(checkBeforeAll && checkAfterAll) && !(checkBeforeEach && checkAfterEach)) {
        throw IllegalArgumentException("checkOnlyNewErrors=true requires before AND after ($this)")
      }
    }
  }

  override fun beforeAll(context: ExtensionContext?) = if (checkBeforeAll) checkVFS(context!!) else Unit

  override fun afterAll(context: ExtensionContext?) = if (checkAfterAll) checkVFS(context!!) else Unit

  override fun beforeEach(context: ExtensionContext?) = if (checkBeforeEach) checkVFS(context!!) else Unit

  override fun afterEach(context: ExtensionContext?) = if (checkAfterEach) checkVFS(context!!) else Unit

  @Suppress("TestOnlyProblems")
  companion object {
    @JvmStatic
    fun beforeAndAfterAll() = CheckVFSHealthExtension(checkBeforeAll = true, checkAfterAll = true)

    @JvmStatic
    fun beforeAndAfterEach() = CheckVFSHealthExtension(checkBeforeEach = true, checkAfterEach = true)

    @JvmStatic
    private val LOCAL_NAMESPACE: ExtensionContext.Namespace = ExtensionContext.Namespace.create("CheckVFSHealthExtension")
  }


  @Throws(Exception::class)
  private fun checkVFS(context: ExtensionContext) {
    //TODO RC: check SkipVFSHealthCheck !in context.element.get().annotations

    //TODO RC: supply dummy LOG instance so VFSHealthChecker doesn't fill the log with
    //         warnings?
    val checker = VFSHealthChecker()
    @Suppress("RAW_RUN_BLOCKING")
    val currentReport = runBlocking {
      checker.checkHealth(checkForOrphanRecords = true)
    }
    context.publishReportEntry(LOCAL_STORE_REPORT_KEY, currentReport.toString())

    //In a perfect world, we should check _any_ VFS error.
    // But in our real world we share VFS across many tests, across many test runs -- hence there are too
    // many chances to get failure completely unrelated to the current test.
    // So we check VFS errors before AND after the test/class, and fail only if they differ -- which strongly
    // suggests VFS error was created in the test.
    if (checkOnlyNewErrors) {
      val localStore = context.getStore(LOCAL_NAMESPACE)
      val previousReport = localStore.get(LOCAL_STORE_REPORT_KEY, VFSHealthCheckReport::class.java)
      if (previousReport == null) {
        //first check: just put
        localStore.put(LOCAL_STORE_REPORT_KEY, currentReport)
      }
      else {
        //second check: compare
        assertVFSErrorsAreNotIncreased(currentReport, previousReport)
      }
    }
    else {
      if (!currentReport.healthy) {
        throw AssertionError("VFS has errors: $currentReport")
      }
    }
  }
}

/**
 * Checks VFS does not gain NEW errors during the test run.
 * VFS health-check could take a while (100ms..few seconds), so to amortize the costs is usually better to apply
 * this rule on a class level, or use it for long-running tests.
 * Use [com.intellij.testFramework.junit5.impl.CheckVFSHealthExtension] for junit5
 */
class CheckVFSHealthRule : TestRule {
  override fun apply(base: Statement,
                     description: Description): Statement {
    if (description.annotations.any { it.annotationClass == SkipVFSHealthCheck::class }) {
      return base
    }

    return object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        //TODO RC: supply dummy LOG instance so VFSHealthChecker doesn't fill the log with
        //         warnings?
        val reportBefore = runBlocking { VFSHealthChecker().checkHealth(checkForOrphanRecords = true) }
        try {
          base.evaluate()
        }
        finally {
          val reportAfter = runBlocking { VFSHealthChecker().checkHealth(checkForOrphanRecords = true) }
          assertVFSErrorsAreNotIncreased(reportAfter, reportBefore)
        }
      }
    }
  }

}

/** Don't check VFS health for the method annotated by this */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SkipVFSHealthCheck

private fun assertVFSErrorsAreNotIncreased(reportAfter: VFSHealthCheckReport,
                                           reportBefore: VFSHealthCheckReport) {
  if (!reportAfter.healthy
      && !reportAfter.hasSameErrors(reportBefore)) {
    //MAYBE RC: if VFS errors _decreased_ during the test run -- this shouldn't be an error
    //          (could be e.g. if VFS was reset during test run, or erroneous records were removed)
    throw AssertionError(
      "VFS got _new_ errors during test execution: \n" +
      "was :$reportBefore\n" +
      "is  :$reportAfter"
    )
  }
}
