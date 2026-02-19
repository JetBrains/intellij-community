// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

internal class AddKotlinCoroutinesQuickFixProvider : AddKotlinLibraryQuickFixProvider(
    libraryGroupId = "org.jetbrains.kotlinx",
    libraryArtifactId = "kotlinx-coroutines-core",
    libraryDescriptorProvider = LibraryDescriptorProvider.default(),
    libraryAvailabilityTester = LibraryAvailabilityTester.knownClassFqn("kotlinx.coroutines.CoroutineScope"),
    libraryReferenceTester = LibraryReferenceTester.knownNames(
        "runBlocking", "CoroutineScope", "Dispatchers", "launch", "GlobalScope",
        "MainScope", "async", "await", "withContext", "cancel", "isActive", "Job",
    ),
    quickFixText = KotlinBundle.message("add.kotlin.coroutines")
)


internal class AddKotlinTestLibraryQuickFixProvider : AddKotlinLibraryQuickFixProvider(
    libraryGroupId = "org.jetbrains.kotlin",
    libraryArtifactId = "kotlin-test",
    libraryDescriptorProvider = LibraryDescriptorProvider.default(),
    libraryAvailabilityTester = LibraryAvailabilityTester.knownClassFqn("kotlin.test.Asserter"),
    libraryReferenceTester = LibraryReferenceTester.knownNames("Test", "assertEquals", "assertTrue", "assertFalse", "assertFailsWith"),
)
