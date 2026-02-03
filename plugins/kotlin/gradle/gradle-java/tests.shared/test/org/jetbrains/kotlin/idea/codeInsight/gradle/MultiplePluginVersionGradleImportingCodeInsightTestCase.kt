// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.runAll

abstract class MultiplePluginVersionGradleImportingCodeInsightTestCase : MultiplePluginVersionGradleImportingTestCase() {
    protected val codeInsightTestFixture: CodeInsightTestFixture get() = _codeInsightTestFixture!!
    private var _codeInsightTestFixture: CodeInsightTestFixture? = null

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
        _codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        codeInsightTestFixture.setUp()
    }

    override fun tearDownFixtures() = runAll(
        ThrowableRunnable { codeInsightTestFixture.tearDown() },
        ThrowableRunnable { _codeInsightTestFixture = null },
        ThrowableRunnable { resetTestFixture() },
    )
}