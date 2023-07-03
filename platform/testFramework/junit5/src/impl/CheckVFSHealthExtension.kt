// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthChecker
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Checks VFS to be consistent before/after all/each tests have passed.
 */
@TestOnly
class CheckVFSHealthExtension
private constructor(private val checkBeforeEach: Boolean = false,
                    private val checkAfterEach: Boolean = false,
                    private val checkBeforeAll: Boolean = false,
                    private val checkAfterAll: Boolean = false) : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  init {
    if (!checkBeforeAll && !checkBeforeEach && !checkAfterAll && !checkAfterEach) {
      throw IllegalArgumentException(
        "All check[Before|After][All|Each] flags are false -- nothing to do. At least one of flags must be true")
    }
  }

  override fun beforeAll(context: ExtensionContext?) = if (checkBeforeAll) checkVFS(context!!) else Unit

  override fun afterAll(context: ExtensionContext?) = if (checkAfterAll) checkVFS(context!!) else Unit

  override fun beforeEach(context: ExtensionContext?) = if (checkBeforeEach) checkVFS(context!!) else Unit

  override fun afterEach(context: ExtensionContext?) = if (checkAfterEach) checkVFS(context!!) else Unit

  @Suppress("TestOnlyProblems")
  companion object {
    @JvmStatic
    fun beforeAll() = CheckVFSHealthExtension(checkBeforeAll = true)

    @JvmStatic
    fun afterAll() = CheckVFSHealthExtension(checkAfterAll = true)

    @JvmStatic
    fun beforeAndAfterAll() = CheckVFSHealthExtension(checkBeforeAll = true, checkAfterAll = true)


    @JvmStatic
    fun beforeEach() = CheckVFSHealthExtension(checkBeforeEach = true)

    @JvmStatic
    fun afterEach() = CheckVFSHealthExtension(checkAfterEach = true)

    @JvmStatic
    fun beforeAndAfterEach() = CheckVFSHealthExtension(checkBeforeEach = true, checkAfterEach = true)
  }


  @Throws(Exception::class)
  private fun checkVFS(context: ExtensionContext) {
    val checker = VFSHealthChecker()
    val report = checker.checkHealth(checkForOrphanRecords = true)
    context.publishReportEntry("vfs-report", report.toString())
    if (!report.healthy) {
      throw AssertionError("VFS is corrupted: $report")
    }
  }
}
