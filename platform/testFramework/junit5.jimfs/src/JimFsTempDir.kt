// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.jimfs

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.io.TempDir

/**
 * Arguments, annotated by it, are set to temporary dir in memory.
 * See [JimFsTempDirFactory]
 * Example
 * ```kotlin
 * class Test {
 *   @Test
 *   fun tempDir(@JimFsTempDir tempDir: Path) {
 *   }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@TempDir(factory = JimFsTempDirFactory::class)
annotation class JimFsTempDir
