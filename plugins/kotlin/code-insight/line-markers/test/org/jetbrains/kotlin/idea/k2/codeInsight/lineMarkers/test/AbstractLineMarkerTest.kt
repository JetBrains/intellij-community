// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.Tag

abstract class AbstractLineMarkerTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    protected fun performTest() {
        myFixture.configureByMainPathStrippingTags("lineMarker")
        myFixture.doHighlighting()

        val document = editor.document

        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)
            .map { Tag(it.startOffset, it.endOffset, "lineMarker", "text" to it.lineMarkerTooltip) }

        val expectedText = KotlinTestHelpers.insertTags(document.text, lineMarkers)

        KotlinTestHelpers.assertEqualsToPath(mainPath, expectedText)
    }

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
}