// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.analyzeDependencies

import com.intellij.analysis.AnalysisScope
import com.intellij.packageDependencies.ForwardDependenciesBuilder
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinAnalyzeDependenciesTest: KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean {
        return true
    }

    fun testXmlReferences() {
        val kotlinClass = myFixture.addFileToProject("myPack/MyClass.kt", "package myPack\nclass MyClass")
        val file = myFixture.configureByText("plugin.xml", "<root impl=\"myPack.MyClass\"")
        val builder = ForwardDependenciesBuilder(getProject(), AnalysisScope(file));
        builder.analyze();
        assertTrue(builder.dependencies.get(file)!!.contains(kotlinClass))
    }
}