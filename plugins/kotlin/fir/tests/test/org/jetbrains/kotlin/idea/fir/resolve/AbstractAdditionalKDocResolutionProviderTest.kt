// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.AdditionalKDocResolutionProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractAdditionalKDocResolutionProviderTest : AbstractFirReferenceResolveTest() {
    private object AdditionalKDocResolutionProviderForTest: AdditionalKDocResolutionProvider {
        /**
         * This function returns symbols whose name is the same as `fqName.shortName().asString()` among declarations
         * in the file where [contextElement] is located. Note that the symbols does not have FqNames that matches
         * FqName of [contextElement]. Therefore, this can show AdditionalKDocResolutionProvider works as we intended.
         */
        override fun resolveKdocFqName(
            analysisSession: KaSession,
            fqName: FqName,
            contextElement: KtElement
        ): Collection<KaSymbol> {
            with(analysisSession) {
                return contextElement.containingKtFile.declarations
                    .filter { it.name == fqName.shortName().asString() }
                    .map { it.symbol }
            }
        }
    }

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().extensionArea.getExtensionPoint(AdditionalKDocResolutionProvider.EP_NAME)
            .registerExtension(AdditionalKDocResolutionProviderForTest, testRootDisposable)
    }
}