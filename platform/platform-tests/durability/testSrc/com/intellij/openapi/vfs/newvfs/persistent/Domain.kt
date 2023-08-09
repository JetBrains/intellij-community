// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.random.Random

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

interface AppController: AutoCloseable {
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