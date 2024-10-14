// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.analysis.KotlinIdeInjectedFilesAnalysisPromoter.Companion.EP_NAME

/**
 * Interface for promoting analysis of Kotlin injected files within the IDE.
 *
 * Typically, injected Kotlin files are ignored during the analysis since no configuration is provided for them.
 * If some plugin wants to support code insight, this interface should be implemented.
 */
@ApiStatus.Internal
interface KotlinIdeInjectedFilesAnalysisPromoter {
    fun shouldRunAnalysisForInjectedFile(viewProvider: FileViewProvider): Boolean

    fun shouldRunOnlyEssentialHighlightingForInjectedFile(psiFile: PsiFile): Boolean

    companion object {
        internal val EP_NAME: ExtensionPointName<KotlinIdeInjectedFilesAnalysisPromoter> =
            ExtensionPointName.Companion.create("org.jetbrains.kotlin.kotlinInjectedFilesAnalysisProvider")
    }
}

val FileViewProvider.isInjectedFileShouldBeAnalyzed: Boolean
    get() {
        return this is InjectedFileViewProvider && EP_NAME.extensionList.any { it.shouldRunAnalysisForInjectedFile(this) }
    }

val PsiFile.injectionRequiresOnlyEssentialHighlighting: Boolean
    get() = viewProvider is InjectedFileViewProvider && EP_NAME.extensionList.any { it.shouldRunOnlyEssentialHighlightingForInjectedFile(this) }