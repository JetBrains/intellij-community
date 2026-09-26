package com.intellij.driver.sdk.remoteDev

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BeControlAdapter(
  val value: KClass<out Any>
)

