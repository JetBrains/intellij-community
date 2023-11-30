package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Remote("com.intellij.openapi.project.DumbService")
interface DumbService {
  fun isDumb(): Boolean
}

fun Driver.waitForSmartMode(project: Project, timeout: Duration = 1.minutes) {
  val dumbService = service<DumbService>(project)

  waitFor(timeout, errorMessage = "Failed to wait ${timeout.inWholeSeconds}s for smart mode") {
    !dumbService.isDumb()
  }
}