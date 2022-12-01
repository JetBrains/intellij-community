// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.ExpectedHighlightingData
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase

abstract class AbstractLineMarkerTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    protected fun performTest() {
        myFixture.configureByDefaultFile()

        val document = editor.document
        val data = ExpectedHighlightingData(document)
        data.init()

        myFixture.doHighlighting()


        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)
        data.checkLineMarkers(myFixture.file, lineMarkers, document.text)
    }

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
}