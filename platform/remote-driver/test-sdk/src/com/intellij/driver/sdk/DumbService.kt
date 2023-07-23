package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import java.time.Duration

@Remote("com.intellij.openapi.project.DumbService")
interface DumbService {
  fun isDumb(): Boolean
}

fun Driver.waitForSmartMode(project: Project, timeout: Duration = Duration.ofMinutes(1)) {
  val dumbService = service(DumbService::class, project)

  waitFor(timeout, errorMessage = "Failed to wait ${timeout.seconds}s for smart mode") {
    !dumbService.isDumb()
  }
}