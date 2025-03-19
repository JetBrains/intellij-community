package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractSharedK1LocalInspectionTest : AbstractLocalInspectionTest() {

    override fun collectHighlightInfos(): List<HighlightInfo> {
        (file as KtFile).analyzeWithAllCompilerChecks()

        return super.collectHighlightInfos()
    }
}