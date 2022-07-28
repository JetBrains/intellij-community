// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.api

/**
 * Enumeration of all known core gradle plugins.
 * If this API goes as public, then this approach should be replaced with extension points.
 */
enum class GradleStaticPluginEntry(val pluginName: String) {
  LIFECYCLE_BASE("lifecycle-base"),
  BASE("base"),
  JVM_ECOSYSTEM("jvm-ecosystem"),
  JVM_TOOLCHAINS("jvm-toolchains"),
  REPORTING_BASE("reporting-base"),
  JAVA_BASE("java-base"),
  JAVA("java");
}