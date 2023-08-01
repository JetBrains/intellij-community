// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinExpectedHighlightingData
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractLineMarkerTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    private fun checkLineMarkers() {
        myFixture.configureDependencies()
        myFixture.configureByDefaultFile()
        val file = testRootPath.resolve(testMethodPath).toFile()
        val fileText = FileUtil.loadFile(file)
        ConfigLibraryUtil.configureLibrariesByDirective(myFixture.module, fileText)
        InTextDirectivesUtils.findStringWithPrefixes(fileText, "METHOD_SEPARATORS")?.let {
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = true
        }
        val disabledOptions = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// OPTION: ")
            .mapNotNull {
                if (it.startsWith("-")) {
                    val optionName = it.substring(1)
                    val field = KotlinLineMarkerOptions::class.java.getDeclaredField(optionName)
                    field.isAccessible = true
                    field.get(KotlinLineMarkerOptions) as GutterIconDescriptor
                } else {
                    null
                }
            }
        disabledOptions.forEach {
            LineMarkerSettings.getSettings().setEnabled(it, false)
        }
        val document = editor.document
        val data = KotlinExpectedHighlightingData(document)
        data.init()

        myFixture.doHighlighting()


        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)
        try {
            ActionUtil.underModalProgress(myFixture.project, "") { data.checkLineMarkers(myFixture.file, lineMarkers, document.text) }
        } finally {
            ConfigLibraryUtil.unconfigureLibrariesByDirective(module, fileText)
            DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = false
            disabledOptions.forEach {
                LineMarkerSettings.getSettings().setEnabled(it, true)
            }
        }
    }

    protected fun performTest() {
        IgnoreTests.runTestIfNotDisabledByFileDirective(testRootPath.resolve(testMethodPath), IgnoreTests.DIRECTIVES.IGNORE_FIR) {
            checkLineMarkers()
        }

    }

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
}