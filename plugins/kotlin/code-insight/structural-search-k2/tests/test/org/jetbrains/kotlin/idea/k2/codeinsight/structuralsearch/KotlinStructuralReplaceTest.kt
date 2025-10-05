// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.structuralsearch.Matcher
import com.intellij.structuralsearch.PatternContext
import com.intellij.structuralsearch.impl.matcher.compiler.StringToConstraintsTransformer
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo
import com.intellij.structuralsearch.plugin.replace.impl.Replacer
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.SmartList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

abstract class KotlinStructuralReplaceTest : KotlinLightCodeInsightFixtureTestCase() {

    private val searchConfiguration = SearchConfiguration().apply {
        name = "SSR"
        matchOptions.setFileType(KotlinFileType.INSTANCE)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun runInDispatchThread(): Boolean {
        return false
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    protected fun doTest(
        searchPattern: String,
        replacePattern: String,
        @Language("kotlin") match: String,
        @Language("kotlin") result: String,
        reformat: Boolean = false,
        shortenFqNames: Boolean = false,
        context: PatternContext = KotlinStructuralSearchProfile.DEFAULT_CONTEXT
    ) {
        myFixture.configureByText(KotlinFileType.INSTANCE, match)
        runBlocking {
            val matchOptions = searchConfiguration.matchOptions.apply {
                fillSearchCriteria(searchPattern)
                patternContext = context
                setFileType(KotlinFileType.INSTANCE)
                scope = readAction {
                    GlobalSearchScopes.openFilesScope(project)
                }
            }
            val sink = async(Dispatchers.IO) {
                val matcher = Matcher(project, matchOptions)
                val sink = CollectingMatchResultSink()
                matcher.findMatches(sink)
                sink
            }.await()
            val replaceOptions = ReplaceConfiguration(searchConfiguration).replaceOptions.apply {
                isToReformatAccordingToStyle = reformat
                isToShortenFQN = shortenFqNames
                StringToConstraintsTransformer.transformCriteria(replacePattern, matchOptions)
                replacement = matchOptions.searchPattern
            }
            invokeAndWaitIfNeeded {
                val replacer = Replacer(project, replaceOptions)
                val replacements: MutableList<ReplacementInfo> = SmartList()
                sink.matches.mapTo(replacements, replacer::buildReplacement)
                myFixture.project.executeWriteCommand("Structural Replace") { replacements.forEach(replacer::replace) }
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
                assertEquals(result, myFixture.file.text)
            }
        }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}