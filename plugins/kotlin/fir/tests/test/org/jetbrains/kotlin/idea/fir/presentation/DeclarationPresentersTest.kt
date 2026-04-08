// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.presentation

import com.intellij.ide.IconProvider
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.presentation.KotlinDefaultNamedDeclarationPresentation
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class DeclarationPresentersTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    fun testGetIconWithNoExtraIconProviders() {
        myFixture.configureByText("Foo.kt", "class <caret>Foo")
        val element = myFixture.elementAtCaret as KtNamedDeclaration

        val firstProvider = IconProvider.EXTENSION_POINT_NAME.findFirstAssignableExtension(KotlinIconProvider::class.java)!!
        val expectedIcon = firstProvider.getIcon(element, Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)

        assertEquals(expectedIcon, KotlinDefaultNamedDeclarationPresentation(element).getIcon(false))
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

        iconProvider.icon = fakeIcon
        assertEquals(fakeIcon, KotlinDefaultNamedDeclarationPresentation(element).getIcon(false))

        iconProvider.icon = null
        assertEquals(defaultIcon, KotlinDefaultNamedDeclarationPresentation(element).getIcon(false))
    }
}
