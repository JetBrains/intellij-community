// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.LibraryType
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import kotlin.test.assertIsNot

object LibraryKindsChecker : AbstractTestChecker<Unit>() {
    override fun KotlinMppTestsContext.check() {
        val libraries = testProject.modules.flatMap { module ->
            module.rootManager.orderEntries
                .filterIsInstance<LibraryOrderEntry>()
                .map { it.library as LibraryEx }
        }
        for (library in libraries) {
            val kind = library.kind ?: continue
            assertIsNot<UnknownLibraryKind>(LibraryType.findByKind(kind))
        }
    }

    override fun createDefaultConfiguration() = Unit
}