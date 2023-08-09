// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.psi

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.AbstractInjectionTest

abstract class K2AbstractInjectionTest : AbstractInjectionTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    protected fun doK2InjectionPresentTest(
        @Language("kotlin") text: String, @Language("Java") javaText: String? = null,
        languageId: String? = null, unInjectShouldBePresent: Boolean = true,
        shreds: List<ShredInfo>? = null,
        injectedText: String? = null
    ) {
        allowAnalysisOnEdt {
            doInjectionPresentTest(text, javaText, languageId, unInjectShouldBePresent, shreds, injectedText)
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun doRemoveInjectionTest(@Language("kotlin") before: String, @Language("kotlin") after: String) {
        allowAnalysisOnEdt {
            super.doRemoveInjectionTest(before, after)
        }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}