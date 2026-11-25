// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate.valueClasses

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KotlinEqualsHashCodeGeneratorExtension
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.isFullValueClass
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.isInlineValueClassWithCustomEquals
import org.jetbrains.kotlin.psi.KtClass

/**
 * Gives the value class templates of [KotlinFullValueClassesTemplateManager] to a full value class, and to an inline value class that can
 * declare `equals` and `hashCode`. The default IntelliJ template gives red code for both.
 */
internal class KotlinFullValueClassEqualsHashCodeGeneratorExtension : KotlinEqualsHashCodeGeneratorExtension {
    override fun isApplicable(klass: KtClass): Boolean =
        templateBaseNameFor(klass) != null

    context(session: KaSession)
    override fun extraEqualsContext(klass: KtClass): Map<String, Any> =
        emptyMap()

    context(session: KaSession)
    override fun extraHashCodeContext(klass: KtClass): Map<String, Any> =
        emptyMap()

    override fun alternativeDefaultTemplateFor(klass: KtClass): String? =
        templateBaseNameFor(klass)

    override fun getTemplatesFor(klass: KtClass): List<TemplateResource> {
        val baseName = templateBaseNameFor(klass) ?: return emptyList()
        return KotlinFullValueClassesTemplateManager.getInstance().templatesWithBaseName(baseName)
    }

    private fun templateBaseNameFor(klass: KtClass): String? =
        when {
            // TODO: Has to be dropped when KTIJ-40105 is fixed
            klass.isFullValueClass() -> KotlinFullValueClassesTemplateManager.BASE_TEMPLATE_NAME
            klass.isInlineValueClassWithCustomEquals() -> KotlinFullValueClassesTemplateManager.INLINE_TEMPLATE_NAME
            else -> null
        }

    override fun isExtensionTemplate(name: String): Boolean =
        KotlinFullValueClassesTemplateManager.getInstance().defaultTemplates.any { extensionTemplate ->
            val templateBaseName = EqualsHashCodeTemplatesManagerBase.getTemplateBaseName(extensionTemplate)
            templateBaseName == name
        }

    override fun isExtensionTemplateFieldsEditable(name: String): Boolean =
        isExtensionTemplate(name)
}
