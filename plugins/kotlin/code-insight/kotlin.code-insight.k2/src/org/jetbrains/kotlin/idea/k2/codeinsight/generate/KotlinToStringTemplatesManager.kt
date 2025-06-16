// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.java.generate.exception.TemplateResourceException
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.java.generate.template.TemplatesManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import java.io.IOException

@State(name = "KotlinToStringTemplates", storages = [Storage("kotlinToStringTemplates.xml")], category = SettingsCategory.CODE)
class KotlinToStringTemplatesManager : TemplatesManager() {
    override fun getDefaultTemplates(): List<TemplateResource> {
        try {
            return listOf(
                TemplateResource(KotlinBundle.message("action.generate.tostring.template.single"), readFile(DEFAULT_SINGLE), true),
                TemplateResource(KotlinBundle.message("action.generate.tostring.template.single.with.super"), readFile(DEFAULT_SINGLE_SUPER_CALL), true),

                TemplateResource(KotlinBundle.message("action.generate.tostring.template.multiple"), readFile(DEFAULT_MULTIPLE), true),
                TemplateResource(KotlinBundle.message("action.generate.tostring.template.multiple.with.super"), readFile(DEFAULT_MULTIPLE_SUPER_CALL), true),
            )

        } catch (e: IOException) {
            throw TemplateResourceException("Error loading default templates", e)
        }
    }

    override fun getInitialTemplateName(): String {
        return DEFAULT_SINGLE
    }

    companion object {
        const val DEFAULT_SINGLE: String = "singleExpr.vm"
        const val DEFAULT_SINGLE_SUPER_CALL: String = "singleExprWithSuper.vm"
        const val DEFAULT_MULTIPLE: String = "multipleExprs.vm"
        const val DEFAULT_MULTIPLE_SUPER_CALL: String = "multipleExprsWithSuper.vm"

        fun getInstance(): TemplatesManager {
            return ApplicationManager.getApplication().getService(KotlinToStringTemplatesManager::class.java)
        }

        @Throws(IOException::class)
        fun readFile(resource: String?): String {
            return readFile(resource, KotlinToStringTemplatesManager::class.java)
        }
    }
}
