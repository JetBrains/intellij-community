// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.random.Random

/**
 * [User] runs in the current process, and deliver some commands to [App] via [UserAgent].
 * Basically, [User] encapsulates 'test scenario'.
 *
 * [App] runs in another process, receives commands from [User] with help of [AppAgent], applies those commands to
 * the component-under-test, and delivers result(s) back to the [User].
 * Basically, it is an event loop, the mediator between wire protocol and actual component-under-stress.
 *
 * [AppController] is a handle to a dedicated process App runs into. It provides access to that process's stdin/stdout,
 * isAlive, and kill.
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