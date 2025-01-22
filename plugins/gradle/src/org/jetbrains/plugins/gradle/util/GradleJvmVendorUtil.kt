// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmVendorUtil")
@file:ApiStatus.Internal
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkProduct
import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JdkVersionDetector.Variant

private val CUSTOM_KNOWN_JVM_VENDOR_MATCHERS = mapOf(
  JvmVendor.KnownJvmVendor.ADOPTIUM to "eclipse".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.ADOPTOPENJDK to "adopt".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.AMAZON to "corretto".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.AZUL to "azul|zulu|azul zulu".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.BELLSOFT to "liberica".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.GRAAL_VM to "graalvm".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.JETBRAINS to "jbr".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.MICROSOFT to "ms".toRegex(RegexOption.IGNORE_CASE),
  JvmVendor.KnownJvmVendor.SAP to "sap".toRegex(RegexOption.IGNORE_CASE)
)

fun String.toJvmVendor(): JvmVendor {
  for ((knownJvmVendor, matcher) in CUSTOM_KNOWN_JVM_VENDOR_MATCHERS.entries) {
    if (matcher.matches(this)) {
      return knownJvmVendor.asJvmVendor()
    }
  }
  val jvmVendor = JvmVendor.fromString(this)
  val knownJvmVendor = jvmVendor.knownVendor
  if (knownJvmVendor != JvmVendor.KnownJvmVendor.UNKNOWN) {
    return knownJvmVendor.asJvmVendor()
  }
  return jvmVendor
}

fun JdkProduct.toJvmVendor(): JvmVendor? {
  return vendor.toJvmVendor()
    .takeIf { it.knownVendor != JvmVendor.KnownJvmVendor.UNKNOWN }
}

fun Variant.toJvmVendor(): JvmVendor? {
  return (prefix ?: name).toJvmVendor()
    .takeIf { it.knownVendor != JvmVendor.KnownJvmVendor.UNKNOWN }
}