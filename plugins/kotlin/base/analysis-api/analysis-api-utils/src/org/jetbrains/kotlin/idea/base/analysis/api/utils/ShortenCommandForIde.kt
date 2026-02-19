// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.components.QualifierToShortenInfo
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ThisLabelToShortenInfo
import org.jetbrains.kotlin.analysis.api.components.TypeToShortenInfo
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * An IDE-specific version of [ShortenCommand] with a possibility to extend and add aditional properties.
 */
@OptIn(KaImplementationDetail::class)
@ApiStatus.Internal
interface ShortenCommandForIde : ShortenCommand {
    val companionReferencesToShorten: List<CompanionReferenceToShorten>
}

@ApiStatus.Internal
data class CompanionReferenceToShorten(val companionReferenceToShorten: SmartPsiElementPointer<KtSimpleNameExpression>)

@OptIn(KaImplementationDetail::class)
internal class ShortenCommandForIdeImpl(
    override val targetFile: SmartPsiElementPointer<KtFile>,
    override val importsToAdd: Set<FqName>,
    override val starImportsToAdd: Set<FqName>,
    override val listOfTypeToShortenInfo: List<TypeToShortenInfo>,
    override val listOfQualifierToShortenInfo: List<QualifierToShortenInfo>,
    override val thisLabelsToShorten: List<ThisLabelToShortenInfo>,
    override val kDocQualifiersToShorten: List<SmartPsiElementPointer<KDocName>>,
    override val companionReferencesToShorten: List<CompanionReferenceToShorten>,
) : ShortenCommandForIde {

    constructor(
        originalCommand: ShortenCommand,
        companionReferencesToShorten: List<CompanionReferenceToShorten>
    ) : this(
        originalCommand.targetFile,
        originalCommand.importsToAdd,
        originalCommand.starImportsToAdd,
        originalCommand.listOfTypeToShortenInfo,
        originalCommand.listOfQualifierToShortenInfo,
        originalCommand.thisLabelsToShorten,
        originalCommand.kDocQualifiersToShorten,
        companionReferencesToShorten,
    )
}
