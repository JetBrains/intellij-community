// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import java.io.Serializable


interface KotlinImportingDiagnostic : Serializable

interface KotlinSourceSetImportingDiagnostic : KotlinImportingDiagnostic {
    val kotlinSourceSet: KotlinSourceSet
}

typealias KotlinImportingDiagnosticsContainer = MutableSet<KotlinImportingDiagnostic>

data class OrphanSourceSetsImportingDiagnostic(override val kotlinSourceSet: KotlinSourceSet) : KotlinSourceSetImportingDiagnostic