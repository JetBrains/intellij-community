// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fe10.analysis

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.imports.KaBaseDefaultImportsProvider
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.DefaultImportProvider

@OptIn(KaImplementationDetail::class)
internal class KaFe10DefaultImportsProvider(private val project: Project) : KaBaseDefaultImportsProvider() {
    override fun getCompilerDefaultImportProvider(targetPlatform: TargetPlatform): DefaultImportProvider =
        targetPlatform.findAnalyzerServices(project)
}