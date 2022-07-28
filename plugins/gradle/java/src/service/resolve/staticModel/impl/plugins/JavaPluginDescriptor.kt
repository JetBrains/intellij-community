// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class JavaPluginDescriptor : GradleStaticPluginDescriptor {
  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.JAVA

  override val dependencies: List<GradleStaticPluginEntry> = listOf(GradleStaticPluginEntry.JAVA_BASE)

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    task("compileJava",
         "Compiles src/main/java.",
         mapOf(
           "classpath" to "org.gradle.api.file.FileCollection",
           "sourceCompatibility" to "java.lang.String",
           "targetCompatibility" to "java.lang.String",
         ))
    task("compileTestJava",
         "Compiles src/test/java.",
         mapOf(
           "classpath" to "org.gradle.api.file.FileCollection",
           "sourceCompatibility" to "java.lang.String",
           "targetCompatibility" to "java.lang.String",
         ))
    configuration("default")
    configuration("testImplementation")
    configuration("testRuntimeOnly")
    configuration("apiElements", "API elements for main.")
    configuration("runtimeElements", "Elements of runtime for main.")
    configuration("mainSourceElements")
    task("javadoc", "Generates Javadoc API documentation for the main source code.", mapOf("destinationDir" to "java.io.File", "title" to "java.lang.String"))
  }
}