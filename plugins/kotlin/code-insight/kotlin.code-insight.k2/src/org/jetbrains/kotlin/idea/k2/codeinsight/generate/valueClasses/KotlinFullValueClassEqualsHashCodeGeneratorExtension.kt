// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate.valueClasses

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KotlinEqualsHashCodeGeneratorExtension
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.psi.KtClass

internal class KotlinFullValueClassEqualsHashCodeGeneratorExtension : KotlinEqualsHashCodeGeneratorExtension {
    override fun isApplicable(klass: KtClass): Boolean =
        // TODO: Has to be dropped when KTIJ-40105 is fixed
        klass.isValue() &&
                klass.languageVersionSettings.supportsFeature(LanguageFeature.FullValueClasses) &&
                KotlinPsiHeuristics.findAnnotation(klass, StandardKotlinNames.Jvm.JvmInline) == null

    context(session: KaSession)
    override fun extraEqualsContext(klass: KtClass): Map<String, Any> =
        emptyMap()

    context(session: KaSession)
    override fun extraHashCodeContext(klass: KtClass): Map<String, Any> =
        emptyMap()

    override fun alternativeDefaultTemplateFor(klass: KtClass): String? =
        if (isApplicable(klass)) {
            KotlinFullValueClassesTemplateManager.BASE_TEMPLATE_NAME
        } else {
            null
        }

    override fun getTemplatesFor(klass: KtClass): List<TemplateResource> =
        if (isApplicable(klass)) {
            KotlinFullValueClassesTemplateManager.getInstance().defaultTemplates
        } else {
            emptyList()
        }

    override fun isExtensionTemplate(name: String): Boolean =
        KotlinFullValueClassesTemplateManager.getInstance().defaultTemplates.any { extensionTemplate ->
            val templateBaseName = EqualsHashCodeTemplatesManagerBase.getTemplateBaseName(extensionTemplate)
            templateBaseName == name
        }

    override fun isExtensionTemplateFieldsEditable(name: String): Boolean =
        isExtensionTemplate(name)
}
