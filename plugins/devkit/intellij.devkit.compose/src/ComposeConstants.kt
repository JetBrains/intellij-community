// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose

import com.intellij.facet.FacetManager
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.idea.facet.KotlinFacetType

// in JPS *.iml definition
internal const val COMPOSE_HOT_RELOAD_ENABLED_MARKER =
  "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"

/**
 * The Compose runtime, as its two Maven coordinates and as the bundled module that wraps it. Every UI toolkit
 * built on Compose composes on the runtime, so it marks a module the tooling here works with.
 */
private val COMPOSE_RUNTIME_LIBRARIES = listOf(
  "androidx.compose.runtime:runtime-desktop",
  "org.jetbrains.compose.runtime:runtime-desktop", // the Compose Multiplatform alias, which a Gradle plugin project declares
  "bundledModule:intellij.libraries.compose.runtime.desktop",
)

internal fun hasCompose(m: Module?): Boolean {
  return JavaLibraryUtil.hasAnyLibraryJar(m, COMPOSE_RUNTIME_LIBRARIES)
}

internal fun hasCompose(p: Project): Boolean {
  return JavaLibraryUtil.hasAnyLibraryJar(p, COMPOSE_RUNTIME_LIBRARIES)
}

private const val KOTLIN_STDLIB_MAVEN = "org.jetbrains.kotlin:kotlin-stdlib"

/** The Kotlin version from which the Compose compiler writes the function key meta annotations by default. */
private const val FUNCTION_KEY_META_KOTLIN = "2.2.20"

/** The Compose runtime version that restored the binary retention of the function key meta annotation. */
private const val FUNCTION_KEY_META_COMPOSE_RUNTIME = "1.11.0"

/**
 * Whether a reload can map a redefined class in [m] back to the composition groups it must invalidate.
 * Compose Hot Reload reads the function key meta annotations for that. The Compose compiler writes them by
 * default from [FUNCTION_KEY_META_KOTLIN] on, but only while the annotation has binary retention. The Compose
 * runtime gave the annotation runtime retention for several versions and restored the binary one in
 * [FUNCTION_KEY_META_COMPOSE_RUNTIME]. A module that neither version serves must therefore ask for the
 * annotations with [COMPOSE_HOT_RELOAD_ENABLED_MARKER] in its Kotlin facet.
 */
internal fun hasFunctionKeyMetaAnnotations(m: Module): Boolean {
  if (writesFunctionKeyMetaByDefault(m)) return true

  val facet = FacetManager.getInstance(m).getFacetByType(KotlinFacetType.TYPE_ID) ?: return false
  val args = facet.configuration.settings.compilerSettings?.additionalArguments ?: return false
  return args.contains(COMPOSE_HOT_RELOAD_ENABLED_MARKER)
}

private fun writesFunctionKeyMetaByDefault(m: Module): Boolean {
  val composeRuntime = COMPOSE_RUNTIME_LIBRARIES.firstNotNullOfOrNull { JavaLibraryUtil.getLibraryVersion(m, it) }
  return isAtLeast(JavaLibraryUtil.getLibraryVersion(m, KOTLIN_STDLIB_MAVEN), FUNCTION_KEY_META_KOTLIN)
         && isAtLeast(composeRuntime, FUNCTION_KEY_META_COMPOSE_RUNTIME)
}

/** A version the module model does not report counts as new enough, so no module is asked for a dead option. */
private fun isAtLeast(version: String?, minimum: String): Boolean {
  return version == null || VersionComparatorUtil.compare(version, minimum) >= 0
}

internal fun isComposeToolingEnabled(): Boolean {
  return AdvancedSettings.getBoolean("devkit.compose.tooling.enabled")
}
