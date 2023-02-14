// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.context

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.fir.codeInsight.HLIndexHelper
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile

internal class FirBasicCompletionContext(
    val parameters: CompletionParameters,
    val sink: LookupElementSink,
    val prefixMatcher: PrefixMatcher,
    val originalKtFile: KtFile,
    val fakeKtFile: KtFile,
    val project: Project,
    val targetPlatform: TargetPlatform,
    val indexHelper: HLIndexHelper,
    val symbolFromIndexProvider: KtSymbolFromIndexProvider,
    val importStrategyDetector: ImportStrategyDetector,
    val lookupElementFactory: KotlinFirLookupElementFactory = KotlinFirLookupElementFactory(),
) {
    val visibleScope = KotlinSourceFilterScope.projectFiles(originalKtFile.resolveScope, project)

    companion object {
        fun createFromParameters(firParameters: KotlinFirCompletionParameters, result: CompletionResultSet): FirBasicCompletionContext? {
            val prefixMatcher = result.prefixMatcher
            val parameters = firParameters.ijParameters
            val originalKtFile = parameters.originalFile as? KtFile ?: return null
            val fakeKtFile = parameters.position.containingFile as? KtFile ?: return null
            val targetPlatform = originalKtFile.platform
            val project = originalKtFile.project
            val indexHelper = createIndexHelper(parameters)

            return FirBasicCompletionContext(
                parameters,
                LookupElementSink(result, firParameters),
                prefixMatcher,
                originalKtFile,
                fakeKtFile,
                project,
                targetPlatform,
                indexHelper,
                KtSymbolFromIndexProvider(project),
                ImportStrategyDetector(originalKtFile, project),
            )
        }

        private fun createIndexHelper(parameters: CompletionParameters): HLIndexHelper {
            return HLIndexHelper.createForPosition(parameters.position)
        }
    }
}