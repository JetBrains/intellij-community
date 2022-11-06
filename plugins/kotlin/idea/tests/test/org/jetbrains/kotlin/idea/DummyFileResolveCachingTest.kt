// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.getFileResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class DummyFileResolveCachingTest : LightJavaCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun test() {
        myFixture.configureByText(KotlinFileType.INSTANCE, "")

        val dummyFileText = "import java.util.Properties"
        val dummyFile = KtPsiFactory(project).createAnalyzableFile("Dummy.kt", dummyFileText, file)

        fun findClassifier(identifier: String) = dummyFile.getResolutionFacade().getFileResolutionScope(dummyFile)
            .findClassifier(Name.identifier(identifier), NoLookupLocation.FROM_IDE)

        assertNotNull(findClassifier("Properties"))

        dummyFile.importDirectives.single().delete()

        assertNull(findClassifier("Properties"))
    }
}