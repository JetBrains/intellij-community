package com.intellij.driver.sdk

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.tools.ide.util.common.logOutput
import java.util.ServiceLoader
import kotlin.time.TimeSource

fun <T> step(name: String, action: () -> T): T = runStep(name, null, action)

fun step(name: String): Unit = runStep(name, null) {}

fun <T> Driver.step(name: String, action: () -> T): T = runStep(name, this, action)

fun <T> Finder.step(name: String, action: () -> T): T = runStep(name, driver, action)

@Deprecated("Use the step functions. The SDK does not load a StepsProvider anymore. To change a step, implement StepInterceptor.")
interface StepsProvider {
  fun <T> step(name: String, action: () -> T): T = runStep(name, null, action)

  fun <T> Driver.step(name: String, action: () -> T): T = runStep(name, this, action)

  fun <T> Finder.step(name: String, action: () -> T): T = runStep(name, driver, action)
}

@Suppress("DEPRECATION")
@Deprecated("Use the step functions. The SDK does not load a StepsProvider anymore.")
class ConsoleStepsProvider : StepsProvider

interface StepInterceptor {
  fun <T> intercept(name: String, action: () -> T): T

  /** Wraps the step [name] of [driver], or of no Driver. Override it when the interceptor needs the Driver of the step. */
  fun <T> intercept(name: String, driver: Driver?, action: () -> T): T = intercept(name, action)
}

private const val GREEN = "\u001B[32m"
private const val RED = "\u001B[31m"
private const val RESET = "\u001B[0m"

private val interceptors: List<StepInterceptor> by lazy {
  ServiceLoader.load(StepInterceptor::class.java, StepInterceptor::class.java.classLoader).toList()
}

private fun <T> runStep(name: String, driver: Driver?, action: () -> T): T {
  val text = "Step '$name'"
  log(driver, "$text started", GREEN) { info(it) }
  val wrapped = interceptors.fold(action) { inner, interceptor -> { interceptor.intercept(name, driver, inner) } }
  val start = TimeSource.Monotonic.markNow()
  try {
    return wrapped().also {
      log(driver, "$text finished in ${start.elapsedNow()}", GREEN) { info(it) }
    }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    log(driver, "$text failed in ${start.elapsedNow()} with '${e.message}'", RED) { warn(it) }
    throw e
  }
}

private fun log(driver: Driver?, text: String, color: String, write: DriverTestLogger.(String) -> Unit) {
  logOutput("$color$text$RESET")
  if (driver == null) return
  try {
    // The log must not wait for the IDE to become idle
    withoutPauseOnIndicators {
      if (driver.isConnected) driver.ideLogger.write(text)
    }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    // The IDE can close at any time, and a step must not fail because of its log
  }
}
