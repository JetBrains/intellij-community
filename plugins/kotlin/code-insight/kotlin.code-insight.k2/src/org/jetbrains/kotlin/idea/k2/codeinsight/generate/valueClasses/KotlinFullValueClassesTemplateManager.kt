// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate.valueClasses

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.java.generate.template.TemplateResource

/**
 * Holds the `equals` and `hashCode` templates for value classes: the [BASE_TEMPLATE_NAME] pair for a full value class, and the
 * [INLINE_TEMPLATE_NAME] pair for an inline value class that can declare `equals` and `hashCode`.
 */
@Service(Level.APP)
@State(name = "KotlinFullValueClassesEqualsHashcodeTemplates", storages = [Storage("kotlinFullValueClassesEqualsHashcodeTemplates.xml")], category = SettingsCategory.CODE)
class KotlinFullValueClassesTemplateManager : EqualsHashCodeTemplatesManagerBase() {
    override fun getDefaultTemplates(): List<TemplateResource> =
        templatePair(BASE_TEMPLATE_NAME, VALUE_CLASS_EQUALS) + templatePair(INLINE_TEMPLATE_NAME, INLINE_VALUE_CLASS_EQUALS)

    /**
     * Returns the `equals` and `hashCode` templates whose base name is [baseName].
     */
    fun templatesWithBaseName(baseName: String): List<TemplateResource> =
        defaultTemplates.filter { getTemplateBaseName(it) == baseName }

    private fun templatePair(baseName: String, equalsFile: String): List<TemplateResource> =
        listOf(
            TemplateResource(
                "$baseName $EQUALS_SUFFIX",
                readFile(equalsFile, KotlinFullValueClassesTemplateManager::class.java),
                true
            ),
            TemplateResource(
                "$baseName $HASH_CODE_SUFFIX",
                readFile(VALUE_CLASS_HASHCODE, KotlinFullValueClassesTemplateManager::class.java),
                true
            ),
        )

    companion object {
        private const val VALUE_CLASS_EQUALS = "valueClassEquals.vm"
        private const val INLINE_VALUE_CLASS_EQUALS = "inlineValueClassEquals.vm"
        private const val VALUE_CLASS_HASHCODE = "valueClassHashcode.vm"
        internal const val BASE_TEMPLATE_NAME: String = "IntelliJ Value Class"
        internal const val INLINE_TEMPLATE_NAME: String = "IntelliJ Inline Value Class"

        fun getInstance(): KotlinFullValueClassesTemplateManager =
            ApplicationManager.getApplication().service<KotlinFullValueClassesTemplateManager>()
    }
}
