// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.util

import com.intellij.lang.Language

/**
 * Presense of this class fixes a lot of red code in plugin.xml files, as the correct `KotlinLanguage` class comes in a binary form
 * (from the Kotlin compiler).
 *
 * This is a temporary hack: resolution in plugin descriptor should be fixed, so it searches not only in project classes, but
 * also in libraries.
 *
 * For instance checks, use `org.jetbrains.kotlin.idea.KotlinLanguage`.
 */
@Suppress("unused")
private class FakeKotlinLanguage : Language("kotlin")