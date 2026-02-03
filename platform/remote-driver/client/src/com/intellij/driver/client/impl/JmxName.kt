package com.intellij.driver.client.impl

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class JmxName(val value: String)