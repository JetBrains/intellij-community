// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinTestUtils")

package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import java.io.File

fun <R> executeOnPooledThreadInReadAction(action: () -> R): R {
    val result = ApplicationManager.getApplication().executeOnPooledThread<R> { runReadAction(action) }
    return result.get()
}

fun k2FileName(
    originalFileName: String,
    testDataDirectory: File,
    k2Extension: IgnoreTests.FileExtension = IgnoreTests.FileExtension.K2,
    vararg additionalExtensions: String
): String {
    val originalFile = File(testDataDirectory, originalFileName)
    val passingDirective = IgnoreTests.DIRECTIVES.FIR_COMPARISON
    val refinedFile = IgnoreTests.getK2TestFileIfK2Passing(originalFile, passingDirective, k2Extension, *additionalExtensions)
    return refinedFile.toRelativeString(testDataDirectory)
}