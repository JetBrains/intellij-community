// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CurrentJavaVersion")
package com.intellij.util

import com.intellij.util.lang.JavaVersion

/**
 * Returns the version of a Java runtime the class is loaded into.
 * The method attempts to parse `"java.runtime.version"` system property first (usually, it is more complete),
 * and falls back to `"java.version"` if the former is invalid or differs in [.feature] or [.minor] numbers.
 */
fun currentJavaVersion(): JavaVersion = currentJavaVersionPlatformSpecificJvm()
