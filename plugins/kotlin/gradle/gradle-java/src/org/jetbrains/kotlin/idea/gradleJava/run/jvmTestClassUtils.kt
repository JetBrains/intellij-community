// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider
import org.jetbrains.kotlin.idea.gradleJava.extensions.KotlinMultiplatformCommonProducersProvider

internal fun ConfigurationFromContext.isJpsJunitConfiguration(): Boolean {
    for (extension in KotlinTestFrameworkProvider.EP_NAME.extensionList) {
        // TODO: Shouldn't we check also isProducedByKotlin()?
        if (extension.isProducedByJava(this)) {
            return true
        }
    }

    return isProducedBy(AbstractPatternBasedConfigurationProducer::class.java)
}

fun ConfigurationFromContext.isProvidedByMultiplatformProducer(): Boolean {
    for (extension in KotlinMultiplatformCommonProducersProvider.EP_NAME.extensionList) {
        if (extension.isProducedByCommonProducer(this)) return true
    }
    return false
}

internal fun canRunJvmTests(): Boolean {
    return KotlinTestFrameworkProvider.EP_NAME.extensionList.any { it.canRunJvmTests }
}

internal fun getTestClassForJvm(location: Location<*>): PsiClass? {
    val element = location.psiElement?.takeIf { it.language == KotlinLanguage.INSTANCE } ?: return null

    for (extension in KotlinTestFrameworkProvider.EP_NAME.extensionList) {
        val testEntity = extension.getJavaTestEntity(element, checkMethod = false) ?: continue
        return testEntity.testClass
    }

    return null
}

internal fun getTestMethodForJvm(location: Location<*>): PsiMethod? {
    val element = location.psiElement?.takeIf { it.language == KotlinLanguage.INSTANCE } ?: return null

    for (extension in KotlinTestFrameworkProvider.EP_NAME.extensionList) {
        return extension.getJavaTestEntity(element, checkMethod = true)?.testMethod ?: continue
    }

    return null
}