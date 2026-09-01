// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.jetbrains.annotations.ApiStatus

/**
 * The code working with project files should use the Eel API: [com.intellij.platform.eel.EelApi].
 * Get [com.intellij.platform.eel.EelApi] as an argument or from [java.nio.file.Path] or `com.intellij.openapi.project.Project]`,
 * and use [com.intellij.platform.eel.EelApi.platform] to check an OS.
 *
 * Showcase: `EelShowCaseTest`
 *
 * ```kotlin
 * suspend fun getOs(p:Project) {
 *   val d = p.getEelDescriptor()
 *   d.osFamily
 *   d.toEelApi().exec.environmentVariables().eelIt().await()
 * }
 * fun getOs(p:Path) {
 *   p.getEelDescriptor().osFamily
 * }
 * ```
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "Please read `LowLevelLocalMachineAccess` annotation doc thoroughly, to make sure you know what you are doing"
)
@ApiStatus.Internal
annotation class LowLevelLocalMachineAccess
