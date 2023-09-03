// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.random.Random

/**
 * [User] runs in the current process, and deliver some commands to [App] via [UserAgent].
 * [App] runs in another process, and receives commands from [User] with help of [AppAgent], applies
 * those commands to the component-under-test, and returns result(s) back to [User].
 * [AppController] is a handle to dedicated process App runs into -- provides stdin/stdout, isAlive, kill.
 *
 */


interface User {
  fun run(userAgent: UserAgent)
}

interface UserAgent {
  val id: Int
  val random: Random
  fun runApplication(body: (AppController) -> Unit)
  fun addInteractionResult(result: InteractionResult)
}

interface App {
  fun run(appAgent: AppAgent)
}

interface AppAgent {
  val input: InputStream
  val output: OutputStream
}

interface AppController : AutoCloseable {
  fun kill()
  fun isAlive(): Boolean
  fun exitCode(): Int

  val appInput: OutputStream
  val appOutput: InputStream

  val workDir: Path
}

data class InteractionCategory(val isOk: Boolean, val category: String)
data class InteractionResult(val category: InteractionCategory, val details: String? = null) {
  constructor(isOk: Boolean, category: String, details: String? = null) : this(InteractionCategory(isOk, category), details)
}

interface StressTestReport {
  val okCount: Long
  val failCount: Long

  val categoryCounts: Map<InteractionCategory, Long>
  val categoryDetails: Map<InteractionCategory, List<String>>
}