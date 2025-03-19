// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiElement
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.name.FqName
import javax.swing.Icon

internal data class SymbolBasedAutoImportVariant(
    val candidatePointer: ImportCandidatePointer,
    override val fqName: FqName,
    override val declarationToImport: PsiElement?,
    override val icon: Icon?,
    override val debugRepresentation: String,
    val statisticsInfo: StatisticsInfo,
    val canNotBeImportedOnTheFly: Boolean,
) : AutoImportVariant {
    override val hint: String = fqName.asString()
}