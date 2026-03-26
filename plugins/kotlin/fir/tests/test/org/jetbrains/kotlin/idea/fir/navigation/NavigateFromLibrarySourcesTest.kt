// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.navigation

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.navigation.AbstractNavigateFromLibrarySourcesTest
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class NavigateFromLibrarySourcesTest : AbstractNavigateFromLibrarySourcesTest() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    private val mockLibraryFacility = MockLibraryFacility(IDEA_TEST_DATA_DIR.resolve("decompiler/navigation/fromLibSource"))

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testJdkClass() {
        checkNavigationFromLibrarySource("Thread", "java.lang.Thread")
    }

    fun testOurKotlinClass() {
        checkNavigationFromLibrarySource("Foo", "a.Foo")
    }

    fun testBuiltinClass() {
        checkNavigationFromLibrarySource("String", "kotlin.String")
    }

    private fun checkNavigationFromLibrarySource(referenceText: String, targetFqName: String) {
        checkNavigationElement(navigationElementForReferenceInLibrarySource("usage.kt", referenceText), targetFqName)
    }

    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    private fun checkNavigationElement(element: PsiElement, expectedFqName: String) {
        when (element) {
            is PsiClass -> assertEquals(expectedFqName, element.qualifiedName)
            is KtClass -> assertEquals(expectedFqName, element.fqName!!.asString())
            else -> fail("Navigation element should be JetClass or PsiClass: " + element::class.java + ", " + element.text)
        }
    }
}
