// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import java.io.Serializable


interface KotlinImportingDiagnostic : Serializable {
    fun deepCopy(cache: MutableMap<Any, Any>): KotlinImportingDiagnostic
}

typealias KotlinImportingDiagnosticsContainer = MutableSet<KotlinImportingDiagnostic>

interface KotlinSourceSetImportingDiagnostic : KotlinImportingDiagnostic {
    val kotlinSourceSet: KotlinSourceSet
}

data class OrphanSourceSetsImportingDiagnostic(override val kotlinSourceSet: KotlinSourceSet) : KotlinSourceSetImportingDiagnostic {
    override fun deepCopy(cache: MutableMap<Any, Any>): OrphanSourceSetsImportingDiagnostic =
        (cache[kotlinSourceSet] as? KotlinSourceSet)?.let { OrphanSourceSetsImportingDiagnostic(it) }
            ?: OrphanSourceSetsImportingDiagnostic(KotlinSourceSetImpl(kotlinSourceSet).apply { cache[kotlinSourceSet] = this })
}