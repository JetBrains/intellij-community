// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.AdditionalKDocResolutionProvider
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractAdditionalKDocResolutionProviderTest : AbstractFirReferenceResolveTest() {
    private object AdditionalKDocResolutionProviderForTest: AdditionalKDocResolutionProvider {
        /**
         * This function returns symbols whose name is the same as `fqName.shortName().asString()` among declarations
         * in the file where [contextElement] is located. Note that the symbols does not have FqNames that matches
         * FqName of [contextElement]. Therefore, this can show AdditionalKDocResolutionProvider works as we intended.
         */
        context(KtAnalysisSession)
        override fun resolveKdocFqName(fqName: FqName, contextElement: KtElement): Collection<KtSymbol> =
            contextElement.containingKtFile.declarations.filter { it.name == fqName.shortName().asString() }.map { it.getSymbol() }
    }

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().extensionArea.getExtensionPoint(AdditionalKDocResolutionProvider.EP_NAME)
            .registerExtension(AdditionalKDocResolutionProviderForTest, testRootDisposable)
    }
}