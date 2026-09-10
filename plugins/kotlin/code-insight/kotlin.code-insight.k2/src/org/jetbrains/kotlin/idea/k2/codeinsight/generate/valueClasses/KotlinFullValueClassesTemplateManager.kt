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

@Service(Level.APP)
@State(name = "KotlinFullValueClassesEqualsHashcodeTemplates", storages = [Storage("kotlinFullValueClassesEqualsHashcodeTemplates.xml")], category = SettingsCategory.CODE)
class KotlinFullValueClassesTemplateManager : EqualsHashCodeTemplatesManagerBase() {
    override fun getDefaultTemplates(): List<TemplateResource> =
        listOf(
            TemplateResource(
                "$BASE_TEMPLATE_NAME $EQUALS_SUFFIX",
                readFile(VALUE_CLASS_EQUALS, KotlinFullValueClassesTemplateManager::class.java),
                true
            ),
            TemplateResource(
                "$BASE_TEMPLATE_NAME $HASH_CODE_SUFFIX",
                readFile(VALUE_CLASS_HASHCODE, KotlinFullValueClassesTemplateManager::class.java),
                true
            ),
        )

    companion object {
        private const val VALUE_CLASS_EQUALS = "valueClassEquals.vm"
        private const val VALUE_CLASS_HASHCODE = "valueClassHashcode.vm"
        internal const val BASE_TEMPLATE_NAME: String = "IntelliJ Value Class"

        fun getInstance(): KotlinFullValueClassesTemplateManager =
            ApplicationManager.getApplication().service<KotlinFullValueClassesTemplateManager>()
    }
}
