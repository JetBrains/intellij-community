// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries

class OrderEntriesChecksConfiguration {
    var excludeDependencies: Regex? = null
    var onlyDependencies: Regex? = null

    var hideStdlib: Boolean = false
    var hideKotlinTest: Boolean = false
    var hideKonanDist: Boolean = false

    /**
     * Enables or disabled sorting of dependencies (based on the lexicographical order of their
     * string representation)
     *
     * This is technically incorrect, because dependencies order matters in general case. However,
     * the majority of test cases don't actually have such a configuration where any possible reordering
     * can cause issues and, actually, change said order quite frequently, leading to a lot of noisy
     * changes in testdata. Therefore, sorting is enabled by default
     */
    var sortDependencies: Boolean = true

    // Always hidden for now
    val hideSelfDependency: Boolean = true
    val hideSdkDependency: Boolean = true
}
