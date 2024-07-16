// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5Jimfs

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.io.TempDir

/**
 * Arguments, annotated by it, are set to temporary dir in memory.
 * See [JimfsTempDirFactory]
 * Example
 * ```kotlin
 * class Test {
 *   @Test
 *   fun tempDir(@JimfsTempDir tempDir: Path) {
 *   }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@TempDir(factory = JimfsTempDirFactory::class)
annotation class JimfsTempDir