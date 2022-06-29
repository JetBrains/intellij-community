// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractJsIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.KotlinJSRunConfigurationDataProvider
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

interface KotlinJSRunConfigurationData {
    val element: PsiElement
    val module: Module
    val jsOutputFilePath: String
}

class Fe10JsIdePlatformKindTooling : AbstractJsIdePlatformKindTooling() {
    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) return null

        return getGenericTestIcon(declaration, { declaration.resolveToDescriptorIfAny() }) {
            val contexts by lazy { computeConfigurationContexts(declaration) }

            val runConfigData = RunConfigurationProducer
                .getProducers(declaration.project)
                .asSequence()
                .filterIsInstance<KotlinJSRunConfigurationDataProvider<*>>()
                .filter { it.isForTests }
                .flatMap { provider -> contexts.map { context -> provider.getConfigurationData(context) } }
                .firstOrNull { it != null }
                ?: return@getGenericTestIcon null

            val location = if (runConfigData is KotlinJSRunConfigurationData) {
                FileUtil.toSystemDependentName(runConfigData.jsOutputFilePath)
            } else {
                declaration.containingKtFile.packageFqName.asString()
            }

            return@getGenericTestIcon SmartList(location)
        }
    }
}