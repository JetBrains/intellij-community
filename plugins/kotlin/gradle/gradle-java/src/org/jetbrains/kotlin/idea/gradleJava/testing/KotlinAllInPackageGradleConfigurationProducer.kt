// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.getSourceFile

open class KotlinAllInPackageGradleConfigurationProducer: AllInPackageGradleConfigurationProducer() {

    override fun getElement(context: ConfigurationContext): PsiPackage? {
        val module = context.module ?: return null
        val psiDirectory = context.psiLocation as? PsiDirectory ?: return null
        if (psiDirectory.sourceRoot == psiDirectory.virtualFile) return null

        val fqNameWithImplicitPrefix = psiDirectory.getFqNameWithImplicitPrefix() ?: return null
        val sourceElement = getSourceElement(module, psiDirectory) ?: return null
        if (getSourceFile(sourceElement) == null) return null

        val psiPackage = JavaPsiFacade.getInstance(context.project).findPackage(fqNameWithImplicitPrefix.asString())
        return psiPackage
    }

}