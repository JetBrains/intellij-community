// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.presentation

import com.intellij.ide.IconProvider
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.Fe10KotlinIconProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class DeclarationPresentersTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testGetIconWithNoExtraIconProviders() {
        myFixture.configureByText("Foo.kt", "class <caret>Foo")
        val element = myFixture.elementAtCaret as KtNamedDeclaration

        // By default, we expect whatever the standard Fe10KotlinIconProvider returns.
        val defaultExpectedIcon = Fe10KotlinIconProvider().getIcon(element, Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)

        assertEquals(defaultExpectedIcon, KotlinDefaultNamedDeclarationPresentation(element).getIcon(false))
    }

    fun testGetIconWithAdditionalIconProvider() {
        myFixture.configureByText("Foo.kt", "class <caret>Foo")
        val element = myFixture.elementAtCaret as KtNamedDeclaration

        val defaultIcon = KotlinDefaultNamedDeclarationPresentation(element).getIcon(false)

        val iconProvider = object : KotlinIconProvider() {
            override fun isMatchingExpected(declaration: KtDeclaration) = false
            var icon: Icon? = null
            override fun getIcon(psiElement: PsiElement, flags: Int) = icon
        }

        val fakeIcon: Icon = object : Icon {
            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}
            override fun getIconWidth() = 0
            override fun getIconHeight() = 0
        }

        IconProvider.EXTENSION_POINT_NAME.point.registerExtension(iconProvider, LoadingOrder.FIRST, testRootDisposable)

        // When the provider has an icon, it should be returned since the provider is first.
        iconProvider.icon = fakeIcon
        assertEquals(fakeIcon, KotlinDefaultNamedDeclarationPresentation(element).getIcon(false))

        // When the provider returns null, execution should continue to the next provider (which returns the original default).
        iconProvider.icon = null
        assertEquals(defaultIcon, KotlinDefaultNamedDeclarationPresentation(element).getIcon(false))
    }
}
