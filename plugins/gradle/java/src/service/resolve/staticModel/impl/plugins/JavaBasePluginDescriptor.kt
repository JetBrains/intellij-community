// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class JavaBasePluginDescriptor : GradleStaticPluginDescriptor {
  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.JAVA_BASE

  override val dependencies: List<GradleStaticPluginEntry> = listOf(
    GradleStaticPluginEntry.BASE,
    GradleStaticPluginEntry.JVM_ECOSYSTEM,
    GradleStaticPluginEntry.REPORTING_BASE,
    GradleStaticPluginEntry.JVM_TOOLCHAINS
  )

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    extension("java", "org.gradle.api.plugins.JavaPluginExtension")
    task("classes", "Assembles src/main/java.")
    task("buildNeeded", "Assembles and tests this project and all projects it depends on.")
    task("buildDependents", "Assembles and tests this project and all projects that depend on it.")
    configuration("implementation", "Implementation only dependencies for java.")
    configuration("compileOnly", "Compile only dependencies for java.")
    configuration("compileClasspath", "Compile classpath for java.")
    configuration("annotationProcessor", "Annotation processors and their dependencies for java.")
    configuration("runtimeClasspath", "Runtime classpath of java.")
  }
}