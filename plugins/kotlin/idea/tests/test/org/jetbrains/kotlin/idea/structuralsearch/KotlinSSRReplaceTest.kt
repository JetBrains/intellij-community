// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch

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
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

abstract class KotlinSSRReplaceTest : BasePlatformTestCase() {
    private val searchConfiguration = SearchConfiguration().apply {
        name = "SSR"
        matchOptions.setFileType(KotlinFileType.INSTANCE)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinLightProjectDescriptor()

    protected fun doTest(
        searchPattern: String,
        replacePattern: String,
        match: String,
        result: String,
        reformat: Boolean = false,
        shortenFqNames: Boolean = false,
        context: PatternContext = KotlinStructuralSearchProfile.DEFAULT_CONTEXT
    ) {
        myFixture.configureByText(KotlinFileType.INSTANCE, match)
        val matchOptions = searchConfiguration.matchOptions.apply {
            fillSearchCriteria(searchPattern)
            patternContext = context
            setFileType(KotlinFileType.INSTANCE)
            scope = GlobalSearchScopes.openFilesScope(project)
        }
        val matcher = Matcher(project, matchOptions)
        val sink = CollectingMatchResultSink()
        matcher.findMatches(sink)
        assert(sink.matches.size > 0) { "Amount of matches should be greater than 0." }
        val replaceOptions = ReplaceConfiguration(searchConfiguration).replaceOptions.apply {
            isToReformatAccordingToStyle = reformat
            isToShortenFQN = shortenFqNames
            StringToConstraintsTransformer.transformCriteria(replacePattern, matchOptions)
            replacement = matchOptions.searchPattern
        }
        val replacer = Replacer(project, replaceOptions)
        val replacements: MutableList<ReplacementInfo> = SmartList()
        sink.matches.mapTo(replacements, replacer::buildReplacement)
        myFixture.project.executeWriteCommand("Structural Replace") { replacements.forEach(replacer::replace) }
        assertEquals(result, myFixture.file.text)
    }
}