package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.remote.Component
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BeControlClass(
  val value: KClass<out BeControlBuilder>
)

interface BeControlBuilder {
  fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component
}