// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.imports

import com.intellij.codeInsight.daemon.ReferenceImporter
import org.jetbrains.kotlin.idea.codeInsight.AbstractKotlinReferenceImporter
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightSettings
import org.jetbrains.kotlin.idea.codeInsight.KotlinAutoImportsFilter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

private class KotlinAutoImportsFilterImpl(val isEnabled: Boolean) : KotlinAutoImportsFilter {
    override fun forceAutoImportForFile(file: KtFile): Boolean = isEnabled

    override fun filterSuggestions(suggestions: Collection<FqName>): Collection<FqName> =
        suggestions.filter { it.asString() == "a.b.AmbiguousClazzForFilter" }.ifEmpty { suggestions }
}

private class TestReferenceImporterImpl(val isEnabled: Boolean) : AbstractKotlinReferenceImporter() {
    override fun isEnabledFor(file: KtFile): Boolean = isEnabled
    override val enableAutoImportFilter: Boolean = true
}

abstract class AbstractFilteringAutoImportTest : AbstractAutoImportTest() {
    override fun setupAutoImportEnvironment(settings: KotlinCodeInsightSettings, withAutoImport: Boolean) {
        // KotlinAutoImportsFilter.forceAutoImportForFile() should work even if addUnambiguousImportsOnTheFly is disabled:
        settings.addUnambiguousImportsOnTheFly = false

        KotlinAutoImportsFilter.EP_NAME.point.registerExtension(
            KotlinAutoImportsFilterImpl(isEnabled = withAutoImport),
            testRootDisposable
        )

        ReferenceImporter.EP_NAME.point.registerExtension(
            TestReferenceImporterImpl(isEnabled = withAutoImport),
            testRootDisposable
        )
    }
}