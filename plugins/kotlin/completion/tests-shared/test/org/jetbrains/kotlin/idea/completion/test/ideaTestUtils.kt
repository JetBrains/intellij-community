// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.openapi.components.ComponentManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.io.File

fun CodeInsightTestFixture.configureByFilesWithSuffixes(mainFile: File, testDataDirectory: File, vararg suffixes: String) {
    val parentFile = mainFile.parentFile ?: error("Parent file not found for $mainFile")
    val baseName = mainFile.nameWithoutExtension.substringBefore(".fir")

    val extraFiles = buildList {
        for (extension in listOf("kt", "java", "properties")) {
            for (suffix in suffixes) {
                val candidate = File(parentFile, "$baseName$suffix.$extension")
                if (candidate != mainFile && candidate.exists()) {
                    add(candidate)
                }
            }
        }
    }

    val allFiles = listOf(mainFile) + extraFiles
    val allPaths = allFiles.map { it.toRelativeString(testDataDirectory) }
    configureByFiles(*allPaths.toTypedArray())
}

inline fun <reified T : Any> Any?.assertInstanceOf() = UsefulTestCase.assertInstanceOf(this, T::class.java)

inline fun <reified T : Any, R> ComponentManager.withComponentRegistered(instance: T, body: () -> R): R {
    val picoContainer = this as ComponentManagerImpl
    val key = T::class.java
    try {
        picoContainer.unregisterComponent(key)
        picoContainer.registerComponentInstance(key, instance)
        return body()
    } finally {
        picoContainer.unregisterComponent(key)
    }
}

fun firFileName(originalFileName: String, testDataDirectory: File, vararg additionalExtensions: String): String {
    val originalFile = File(testDataDirectory, originalFileName)
    val refinedFile = IgnoreTests.getFirTestFileIfFirPassing(originalFile, IgnoreTests.DIRECTIVES.FIR_COMPARISON, *additionalExtensions)
    return refinedFile.toRelativeString(testDataDirectory)
}