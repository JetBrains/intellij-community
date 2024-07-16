// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace

import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature

class GeneralWorkspaceChecksConfiguration {
    var excludedModuleNames: Regex? = null
    var includedModuleNames: Regex? = null

    var hideTestModules: Boolean = false
    var hideProductionModules: Boolean = false

    var disableCheckers: Set<TestFeature<*>>? = null
    var onlyCheckers: Set<TestFeature<*>>? = null

    var testClassifier: String? = null
}
