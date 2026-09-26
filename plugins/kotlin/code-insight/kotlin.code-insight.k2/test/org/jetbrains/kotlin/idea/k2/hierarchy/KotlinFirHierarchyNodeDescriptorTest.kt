// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.test.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.calls.KotlinCallHierarchyNodeDescriptor
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides.KotlinOverrideHierarchyNodeDescriptor
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinTypeHierarchyNodeDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinFirHierarchyNodeDescriptorTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testCallHierarchyStrikesThroughDeprecatedFunction() {
        myFixture.configureByText(
            "Foo.kt",
            """
                @Deprecated("")
                fun <caret>oldFun() {}
            """.trimIndent(),
        )

        val descriptor = KotlinCallHierarchyNodeDescriptor(null, caretElement<KtNamedFunction>(), false, false)

        assertMainSectionStruckOut(descriptor)
    }

    fun testOverrideHierarchyStrikesThroughDeprecatedClass() {
        myFixture.configureByText(
            "Foo.kt",
            """
                open class Base {
                    open fun <caret>foo() {}
                }

                @Deprecated("")
                class Derived : Base() {
                    override fun foo() {}
                }
            """.trimIndent(),
        )

        val file = myFixture.file
        val descriptor = KotlinOverrideHierarchyNodeDescriptor(
            null,
            PsiTreeUtil.findChildrenOfType(file, KtClass::class.java).single { it.name == "Derived" },
            caretElement<KtNamedFunction>(),
        )

        assertMainSectionStruckOut(descriptor)
    }

    fun testTypeHierarchyStrikesThroughDeprecatedClass() {
        myFixture.configureByText(
            "Foo.kt",
            """
                @Deprecated("")
                class <caret>DeprecatedType
            """.trimIndent(),
        )

        val descriptor = KotlinTypeHierarchyNodeDescriptor(project, null, caretElement<KtClass>(), false)

        assertMainSectionStruckOut(descriptor)
    }

    private fun assertMainSectionStruckOut(descriptor: HierarchyNodeDescriptor) {
        val effectType = executeOnPooledThreadInReadAction {
            descriptor.update()
            descriptor.highlightedText.firstSection().textAttributes.effectType
        }

        assertEquals(EffectType.STRIKEOUT, effectType)
    }

    private fun CompositeAppearance.firstSection(): CompositeAppearance.TextSection {
        val iterator = sectionsIterator
        assertTrue(iterator.hasNext())
        return iterator.next()
    }

    private inline fun <reified T : PsiElement> caretElement(): T {
        return PsiTreeUtil.getParentOfType(myFixture.elementAtCaret, T::class.java, false)
            ?: error("No ${T::class.java.simpleName} at caret")
    }
}
