// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

internal interface MultiplatformModelImportingChecker {
    fun check(model: KotlinMPPGradleModel, reportTo: KotlinImportingDiagnosticsContainer, context: MultiplatformModelImportingContext)
}

internal object OrphanSourceSetImportingChecker : MultiplatformModelImportingChecker {
    override fun check(
        model: KotlinMPPGradleModel,
        reportTo: KotlinImportingDiagnosticsContainer,
        context: MultiplatformModelImportingContext
    ) {
        model.sourceSetsByName.values.filter { context.isOrphanSourceSet(it) }
            .mapTo(reportTo) { OrphanSourceSetsImportingDiagnostic(it) }
    }
}