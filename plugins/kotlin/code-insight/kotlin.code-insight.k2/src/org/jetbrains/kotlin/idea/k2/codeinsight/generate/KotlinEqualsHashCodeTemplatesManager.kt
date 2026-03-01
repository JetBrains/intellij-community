// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase
import com.intellij.codeInsight.generation.GenerateEqualsHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.java.generate.exception.TemplateResourceException
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KotlinEqualsHashCodeGeneratorExtension
import org.jetbrains.kotlin.psi.KtClass
import java.io.IOException

@State(name = "KotlinEqualsHashcodeTemplates", storages = [Storage("kotlinEqualsHashcodeTemplates.xml")], category = SettingsCategory.CODE)
class KotlinEqualsHashCodeTemplatesManager : EqualsHashCodeTemplatesManagerBase() {
    override fun getDefaultTemplates(): List<TemplateResource> {
        try {
            return listOf(
                TemplateResource("IntelliJ Default equals", readFile(DEFAULT_EQUALS, KotlinEqualsHashCodeTemplatesManager::class.java), true),
                TemplateResource("IntelliJ Default hashCode", readFile(DEFAULT_HASHCODE, KotlinEqualsHashCodeTemplatesManager::class.java), true)
            )
        } catch (e: IOException) {
            throw TemplateResourceException("Error loading default templates", e)
        }
    }

    override fun getEqualsImplicitVars(project: Project): Map<String, PsiType> {
        val map = GenerateEqualsHelper.getEqualsImplicitVars(project)
        addPlatformVariables(map)
        return map
    }

    private fun addPlatformVariables(map: MutableMap<String, PsiType>) {
        map.put("isCommon", PsiTypes.booleanType())
        map.put("isJs", PsiTypes.booleanType())
        map.put("isNative", PsiTypes.booleanType())
        map.put("isWasm", PsiTypes.booleanType())
    }

    override fun getHashCodeImplicitVars(project: Project): Map<String, PsiType> {
        val map = GenerateEqualsHelper.getHashCodeImplicitVars()
        addPlatformVariables(map)
        return map
    }

    fun <T> runWithExtensionTemplatesFor(ktClass: KtClass, block: () -> T): T {
        val applicableExtension = KotlinEqualsHashCodeGeneratorExtension.getSingleApplicableFor(ktClass)
        var previousDefaultTemplate: TemplateResource? = null
        val extensionTemplates = mutableSetOf<TemplateResource>()

        try {
            val alternativeDefaultTemplate = applicableExtension?.alternativeDefaultTemplateFor(ktClass)
            applicableExtension?.getTemplatesFor(ktClass)
                ?.forEach { additionalTemplate ->
                    addTemplate(additionalTemplate)
                    extensionTemplates.add(additionalTemplate)
                }
            if (alternativeDefaultTemplate != null) {
                previousDefaultTemplate = defaultTemplate
                setDefaultTemplate(alternativeDefaultTemplate)
            }
            return block()
        } finally {
            if (previousDefaultTemplate != null) {
                defaultTemplate = previousDefaultTemplate
            }
            setTemplates(this.allTemplates.filter { it !in extensionTemplates })
        }
    }

    companion object {
        private const val DEFAULT_EQUALS = "defaultEquals.vm"
        private const val DEFAULT_HASHCODE = "defaultHashcode.vm"

        fun getInstance(): KotlinEqualsHashCodeTemplatesManager {
            return ApplicationManager.getApplication().getService(
                KotlinEqualsHashCodeTemplatesManager::class.java
            )
        }
    }
}
