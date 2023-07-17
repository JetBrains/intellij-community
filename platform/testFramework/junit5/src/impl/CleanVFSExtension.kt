// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*

/**
 * Cleans VFS before/after all/each tests have passed.
 */
@TestOnly
class CleanVFSExtension
private constructor(private val cleanBeforeEach: Boolean = false,
                    private val cleanAfterEach: Boolean = false,
                    private val cleanBeforeAll: Boolean = false,
                    private val cleanAfterAll: Boolean = false) : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  init {
    if (!cleanBeforeAll && !cleanBeforeEach && !cleanAfterAll && !cleanAfterEach) {
      throw IllegalArgumentException(
        "All clean[Before|After][All|Each] flags are false -- nothing to do. At least one of flags must be true")
    }
  }

  override fun beforeAll(context: ExtensionContext?) = if (cleanBeforeAll) cleanVFS(context!!) else Unit

  override fun afterAll(context: ExtensionContext?) = if (cleanAfterAll) cleanVFS(context!!) else Unit

  override fun beforeEach(context: ExtensionContext?) = if (cleanBeforeEach) cleanVFS(context!!) else Unit

  override fun afterEach(context: ExtensionContext?) = if (cleanAfterEach) cleanVFS(context!!) else Unit

  @Suppress("TestOnlyProblems")
  companion object {
    @JvmStatic
    fun beforeAll() = CleanVFSExtension(cleanBeforeAll = true)

    @JvmStatic
    fun afterAll() = CleanVFSExtension(cleanAfterAll = true)

    @JvmStatic
    fun beforeAndAfterAll() = CleanVFSExtension(cleanBeforeAll = true, cleanAfterAll = true)


    @JvmStatic
    fun beforeEach() = CleanVFSExtension(cleanBeforeEach = true)

    @JvmStatic
    fun afterEach() = CleanVFSExtension(cleanAfterEach = true)

    @JvmStatic
    fun beforeAndAfterEach() = CleanVFSExtension(cleanBeforeEach = true, cleanAfterEach = true)
  }


  @Throws(Exception::class)
  private fun cleanVFS(context: ExtensionContext) {
    context.publishReportEntry("Request VFS rebuild (for better tests isolation)")

    FSRecords.getInstance().scheduleRebuild("Rebuild VFS (for better tests isolation)", null)
  }
}
