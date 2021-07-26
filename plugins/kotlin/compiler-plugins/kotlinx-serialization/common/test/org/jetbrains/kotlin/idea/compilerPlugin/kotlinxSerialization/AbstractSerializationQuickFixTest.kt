// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlinx.serialization.idea.getSerializationCoreLibraryJar
import org.jetbrains.kotlinx.serialization.idea.getSerializationJsonLibraryJar

abstract class AbstractSerializationQuickFixTest : AbstractQuickFixTest() {
    override fun setUp() {
        super.setUp()
        val coreJar = getSerializationCoreLibraryJar()!!
        val jsonJar = getSerializationJsonLibraryJar()!!
        ConfigLibraryUtil.addLibrary(module, "Serialization core") {
            addRoot(coreJar, OrderRootType.CLASSES)
        }
        ConfigLibraryUtil.addLibrary(module, "Serialization JSON") {
            addRoot(jsonJar, OrderRootType.CLASSES)
        }
    }

    override fun tearDown() {
        ConfigLibraryUtil.removeLibrary(module, "Serialization JSON")
        ConfigLibraryUtil.removeLibrary(module, "Serialization core")

        super.tearDown()
    }
}
