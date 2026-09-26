// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
const val USE_DEFAULT_WSL_DISTRO = "default distro"

/**
 * Test marked with [mandatory] requires WSL to run and will fail without it.
 * You can provide [distroNamePart] i.e "ubuntu" (default distro will be used otherwise).
 *
 * This annotation is repeatable, so you can run a test on several distros.
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@TestApplicationWithEel
annotation class WslTest(val distroNamePart: String = USE_DEFAULT_WSL_DISTRO, val mandatory: Boolean = true)
