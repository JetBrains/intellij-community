// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import org.jetbrains.annotations.TestOnly

/**
 * Test marked with this annotation requires WSL to run and will fail without it..
 */
@TestOnly
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@TestApplicationWithEel
annotation class WslMandatoryTest