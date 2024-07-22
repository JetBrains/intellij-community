// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.java.generate.exception.TemplateResourceException
import org.jetbrains.java.generate.template.TemplateResource
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
