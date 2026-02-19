// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

class KotlinSymbolOnAirResolveTest: KotlinLightCodeInsightFixtureTestCase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    fun testResolveInJavaContext() {
        myFixture.addFileToProject("A.kt", """
            interface A {
               fun <S: String> foo(s: S)
            }
        """.trimIndent())
        val method = myFixture.javaFacade.findClass("A").methods[0]
        val clazz = myFixture.javaFacade.resolveHelper.resolveReferencedClass("S", method)
        assertNotNull(clazz)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}