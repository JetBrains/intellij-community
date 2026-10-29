package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurerService")
interface FUSProjectHotStartUpMeasurerService {
  fun isHandlingFinished(): Boolean
}

/**
 * For production, it's perfectly fine not so send
 * startup statistics from [com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer]
 * when a project was closed during it's opening. But to test such cases, one must enforce that statistic to be written.
 * This is util method for such enforcement
 */
fun Driver.waitForStartupFUSToWrite(timeout: Duration = 10.seconds) {
  waitFor("Fus startup is finished", timeout = timeout, interval = 1.seconds) {
    service(FUSProjectHotStartUpMeasurerService::class).isHandlingFinished()
  }
}