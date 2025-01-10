// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.jps.model.java.JdkVersionDetector.Variant

fun String.toJvmVendor(): JvmVendor {
  return JvmVendor.fromString(this)
}

fun Variant.toJvmVendor(): JvmVendor {
  val knownVendor = toKnownJvmVendor()
  if (knownVendor != JvmVendor.KnownJvmVendor.UNKNOWN) {
    return knownVendor.asJvmVendor()
  }
  return name.toJvmVendor()
}

private fun Variant.toKnownJvmVendor(): JvmVendor.KnownJvmVendor {
  return when (this) {
    Variant.AdoptOpenJdk_HS -> JvmVendor.KnownJvmVendor.ADOPTOPENJDK
    Variant.Corretto -> JvmVendor.KnownJvmVendor.AMAZON
    Variant.GraalVM -> JvmVendor.KnownJvmVendor.GRAAL_VM
    Variant.IBM -> JvmVendor.KnownJvmVendor.IBM
    Variant.JBR -> JvmVendor.KnownJvmVendor.JETBRAINS
    Variant.Liberica -> JvmVendor.KnownJvmVendor.BELLSOFT
    Variant.Microsoft -> JvmVendor.KnownJvmVendor.MICROSOFT
    Variant.Oracle -> JvmVendor.KnownJvmVendor.ORACLE
    Variant.SapMachine -> JvmVendor.KnownJvmVendor.SAP
    Variant.Temurin -> JvmVendor.KnownJvmVendor.ADOPTIUM
    Variant.Zulu -> JvmVendor.KnownJvmVendor.AZUL

    Variant.AdoptOpenJdk_J9 -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.BiSheng -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.Dragonwell -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.GraalVMCE -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.Homebrew -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.Kona -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.Semeru -> JvmVendor.KnownJvmVendor.UNKNOWN
    Variant.Unknown -> JvmVendor.KnownJvmVendor.UNKNOWN
  }
}