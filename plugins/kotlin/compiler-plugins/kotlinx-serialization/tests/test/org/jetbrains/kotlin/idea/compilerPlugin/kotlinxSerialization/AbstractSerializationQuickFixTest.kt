// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot

abstract class AbstractSerializationQuickFixTest : AbstractQuickFixTest() {
    override fun setUp() {
        super.setUp()
        val coreJar = getSerializationCoreLibraryJar()!!
        val jsonJar = getSerializationJsonLibraryJar()!!
        runInEdtAndWait {
            ConfigLibraryUtil.addLibrary(module, "Serialization core") {
                addRoot(coreJar, OrderRootType.CLASSES)
            }
            ConfigLibraryUtil.addLibrary(module, "Serialization JSON") {
                addRoot(jsonJar, OrderRootType.CLASSES)
            }
        }
    }

    override fun tearDown() {
        runAll(
            { runInEdtAndWait {
                try {
                    ConfigLibraryUtil.removeLibrary(module, "Serialization JSON")
                    ConfigLibraryUtil.removeLibrary(module, "Serialization core")
                } catch (e: Throwable) {
                    addSuppressedException(e)
                }
            } },
            { super.tearDown() }
        )
    }
}
