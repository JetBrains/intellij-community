// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.workspace

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.utils.Printer
import java.io.File

internal fun <T> Collection<T>.joinToStringWithSorting(separator: String = ", ", toString: (T) -> String = { it.toString() }): String =
    map { toString(it) }.sorted().joinToString(separator)

internal fun Printer.indented(block: () -> Unit) {
    pushIndent()
    block()
    popIndent()
}

fun checkWorkspaceModel(
    project: Project,
    expectedTestDataDir: File,
    actualTestProjectRoot: File, // root of [project]
    kotlinPluginVersion: KotlinToolingVersion,
    gradleVersion: String,
    checkers: List<WorkspaceModelChecker<*>>,
    testConfiguration: TestConfiguration,
    testClassifier: String? = null,
    agpClassifier: String? = null,
) {
    for (checker in checkers) {
        checker.check(
            project,
            expectedTestDataDir,
            actualTestProjectRoot,
            kotlinPluginVersion,
            gradleVersion,
            testConfiguration,
            testClassifier,
            agpClassifier
        )
    }
}
