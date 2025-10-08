// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.impl.k2.addParamTypesIfNeeded
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.StringTemplateCompletion.correctParametersForInStringTemplateCompletion
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal sealed class KotlinFirCompletionParameters(
    val delegate: CompletionParameters, // delegate as much as possible; use only in corner cases/platform methods
) {

    abstract val type: CorrectionType?

    val invocationCount: Int
        get() = delegate.invocationCount

    val completionType: CompletionType
        get() = delegate.completionType

    val position: PsiElement
        get() = delegate.position

    val offset: Int
        get() = delegate.offset

    val originalFile: KtFile
        get() = delegate.originalKtFile!!

    val completionFile: KtFile
        get() = delegate.completionKtFile!!

    internal class Original private constructor(
        ijParameters: CompletionParameters,
    ) : KotlinFirCompletionParameters(ijParameters) {

        override val type: CorrectionType? get() = null

        companion object {

            fun create(parameters: CompletionParameters): Original? {
                parameters.originalKtFile ?: return null
                parameters.completionKtFile ?: return null

                return Original(parameters)
            }
        }
    }

    internal class Corrected private constructor(
        ijParameters: CompletionParameters,
        val original: CompletionParameters,
        override val type: CorrectionType,
    ) : KotlinFirCompletionParameters(ijParameters) {

        companion object {

            fun create(
                correctedParameters: CompletionParameters,
                originalParameters: CompletionParameters,
                correctionType: CorrectionType,
            ): Corrected? {
                correctedParameters.originalKtFile ?: return null
                correctedParameters.completionKtFile ?: return null

                return Corrected(correctedParameters, originalParameters, correctionType)
            }
        }
    }

    enum class CorrectionType {
        BRACES_FOR_STRING_TEMPLATE,
        INSERTED_TYPE_ARGS,
    }

    companion object {

        // todo reconsider and move
        val KotlinFirCompletionParameters.useSiteModule: KaModule
            get() = originalFile.getKaModule(originalFile.project, useSiteModule = null)

        // todo reconsider and move
        val KotlinFirCompletionParameters.languageVersionSettings: LanguageVersionSettings
            get() = originalFile.project.languageVersionSettings

        private fun correctParametersForTypeArgumentInsertion(parameters: CompletionParameters): KotlinFirCompletionParameters? {
            val parentKtElement = parameters.position.parent as? KtElement ?: return null
            analyze(parentKtElement) {
                val fixedPosition = addParamTypesIfNeeded(parameters.position) ?: return null
                if (fixedPosition == parameters.position) return null

                val offsetInIdentifier = parameters.offset - parameters.position.textOffset
                val correctedParameters = parameters.withPosition(fixedPosition, fixedPosition.textOffset + offsetInIdentifier)
                return Corrected.create(
                    correctedParameters = correctedParameters,
                    originalParameters = parameters,
                    correctionType = CorrectionType.INSERTED_TYPE_ARGS
                )
            }
        }

        fun create(parameters: CompletionParameters): KotlinFirCompletionParameters? {
            correctParametersForTypeArgumentInsertion(parameters)?.let { return it }

            return when (val correctedParameters = correctParametersForInStringTemplateCompletion(parameters)) {
                null -> Original.create(parameters)
                else -> Corrected.create(correctedParameters, parameters, CorrectionType.BRACES_FOR_STRING_TEMPLATE)
            }
        }
        private inline val CompletionParameters.originalKtFile: KtFile?
            get() = originalFile as? KtFile

        private inline val CompletionParameters.completionKtFile: KtFile?
            get() = position.containingFile as? KtFile
    }
}

