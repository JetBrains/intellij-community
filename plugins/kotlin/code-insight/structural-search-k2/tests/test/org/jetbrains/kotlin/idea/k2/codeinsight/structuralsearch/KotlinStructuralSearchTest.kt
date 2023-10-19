// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.openapi.util.text.StringUtil
import com.intellij.structuralsearch.MatchOptions
import com.intellij.structuralsearch.Matcher
import com.intellij.structuralsearch.PatternContext
import com.intellij.structuralsearch.StructuralSearchUtil
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.structuralsearch.inspection.SSBasedInspection
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import com.intellij.structuralsearch.plugin.ui.UIUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.SmartList
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions

abstract class KotlinStructuralSearchTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    private var myInspection: SSBasedInspection? = null

    private val myConfiguration = SearchConfiguration().apply {
        name = "SSR"
        matchOptions.setFileType(KotlinFileType.INSTANCE)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun setUp() {
        super.setUp()
        myInspection = SSBasedInspection()
        myFixture.enableInspections(myInspection)
    }

    protected fun doTest(pattern: String, highlighting: String, context: PatternContext = KotlinStructuralSearchProfile.DEFAULT_CONTEXT) {
        myFixture.configureByText("aaa.kt", highlighting)
        withCustomCompilerOptions(myFixture.file.text, project, module) {
            testHighlighting(pattern, context)
        }
    }

    private fun testHighlighting(pattern: String, context: PatternContext = KotlinStructuralSearchProfile.DEFAULT_CONTEXT) {
        val options = myConfiguration.matchOptions.apply {
            fillSearchCriteria(pattern)
            patternContext = context
        }
        Matcher.validate(project, options)
        val message = checkApplicableConstraints(options, PatternCompiler.compilePattern(project, options, true, false))
        assertNull("Constraint applicability error: $message\n", message)
        StructuralSearchProfileActionProvider.createNewInspection(myConfiguration, project)
        myFixture.testHighlighting(true, false, false)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    companion object {
        fun checkApplicableConstraints(options: MatchOptions, compiledPattern: CompiledPattern): String? {
            val profile = StructuralSearchUtil.getProfileByFileType(options.fileType)!!
            for (varName in options.variableConstraintNames) {
                val nodes = compiledPattern.getVariableNodes(varName)
                val constraint = options.getVariableConstraint(varName)
                val usedConstraints: MutableList<String> = SmartList()
                if (!StringUtil.isEmpty(constraint.regExp)) usedConstraints.add(UIUtil.TEXT)
                if (constraint.isWithinHierarchy) usedConstraints.add(UIUtil.TEXT_HIERARCHY)
                if (constraint.minCount == 0) usedConstraints.add(UIUtil.MINIMUM_ZERO)
                if (constraint.maxCount > 1) usedConstraints.add(UIUtil.MAXIMUM_UNLIMITED)
                if (!StringUtil.isEmpty(constraint.nameOfExprType)) usedConstraints.add(UIUtil.TYPE)
                if (!StringUtil.isEmpty(constraint.nameOfFormalArgType)) usedConstraints.add(UIUtil.EXPECTED_TYPE)
                if (!StringUtil.isEmpty(constraint.referenceConstraint)) usedConstraints.add(UIUtil.REFERENCE)
                usedConstraints.firstOrNull { !profile.isApplicableConstraint(it, nodes, false, constraint.isPartOfSearchResults) }?.let {
                    return@checkApplicableConstraints "$it not applicable for $varName"
                }
            }
            return null
        }
    }
}