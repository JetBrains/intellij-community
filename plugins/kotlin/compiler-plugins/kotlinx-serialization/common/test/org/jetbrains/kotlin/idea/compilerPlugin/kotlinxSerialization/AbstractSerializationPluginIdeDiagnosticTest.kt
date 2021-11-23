// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.runAll
import org.jetbrains.kotlin.ObsoleteTestInfrastructure
import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import java.io.File

@OptIn(ObsoleteTestInfrastructure::class)
abstract class AbstractSerializationPluginIdeDiagnosticTest : AbstractKotlinHighlightVisitorTest() {
    private val serializationLibraries: Map<String, File> = mapOf(
        "serializationCore" to getSerializationCoreLibraryJar()!!,
        "serializationJson" to getSerializationJsonLibraryJar()!!
    )

    override fun setUp() {
        addSerializationLibraries()
        super.setUp()
    }

    override fun tearDown() {
        runAll(
            { removeSerializationLibraries(module) },
            { super.tearDown() }
        )
    }

    private fun addSerializationLibraries() {
        for ((libraryName, libraryJar) in serializationLibraries) {
            ConfigLibraryUtil.addLibrary(module, libraryName) {
                addRoot(libraryJar, OrderRootType.CLASSES)
            }
        }
    }

    private fun removeSerializationLibraries(module: Module) {
        for ((libraryName, _) in serializationLibraries) {
            ConfigLibraryUtil.removeLibrary(module, libraryName)
        }
    }
}
