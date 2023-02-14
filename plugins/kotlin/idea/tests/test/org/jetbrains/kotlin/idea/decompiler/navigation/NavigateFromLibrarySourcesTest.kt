// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(JUnit38ClassRunner::class)
class NavigateFromLibrarySourcesTest : AbstractNavigateFromLibrarySourcesTest() {
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

    // This test is not exactly for navigation, but separating it to another class doesn't worth it.
    fun testLightClassForLibrarySource() {
        val navigationElement = navigationElementForReferenceInLibrarySource("usage.kt", "Foo")
        assertTrue(navigationElement is KtClassOrObject, "Foo should navigate to JetClassOrObject")
        val lightClass = navigationElement.toLightClass()
        assertTrue(
            lightClass is KtLightClassForDecompiledDeclaration,
            "Light classes for decompiled declaration should be provided for library source"
        )
        assertEquals("Foo", lightClass.name)
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
