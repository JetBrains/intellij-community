// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.psi.resolveLink
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile


class JavadocNavigationTest() : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )
    }

    fun testLinkFromPackage() {
        val file = myFixture.configureByText(
            KotlinFileType.INSTANCE, """package foo.bar 
            | class A
            | """.trimMargin()
        ) as KtFile
        val psiPackage = JavaPsiFacade.getInstance(myFixture.project).findPackage(file.packageFqName.asString())!!
        val documentationTarget =
            resolveLink(psiPackage, DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "foo.bar.A")
        assertInstanceOf(documentationTarget, KotlinDocumentationTarget::class.java)
    }
}
