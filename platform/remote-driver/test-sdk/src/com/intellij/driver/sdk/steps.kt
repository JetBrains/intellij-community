package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import java.util.*


interface StepsProvider {

  fun <T> step(name: String, action: () -> T): T

  fun <T> Driver.step(name: String, action: () -> T): T

  fun <T> Finder.step(name: String, action: () -> T): T
}

private val provider: StepsProvider by lazy {
  ServiceLoader.load(StepsProvider::class.java)
    .findFirst()
    .orElse(ConsoleStepsProvider())
}

fun <T> step(name: String, action: () -> T): T = provider.step(name, action)
fun <T> Driver.step(name: String, action: () -> T): T = provider.run { this@step.step(name, action) }
fun <T> Finder.step(name: String, action: () -> T): T = provider.run { this@step.step(name, action) }

class ConsoleStepsProvider : StepsProvider {
  override fun <T> step(name: String, action: () -> T): T = printStep(name, action)

  override fun <T> Driver.step(name: String, action: () -> T): T = printStep(name, action)

  override fun <T> Finder.step(name: String, action: () -> T): T = printStep(name, action)

  private fun <T> printStep(name: String, action: () -> T): T {
    println("Step '$name' is started")
    return action().also { println("Step '$name' is finished") }
  }
}