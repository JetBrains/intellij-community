// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.java.generate.template.TemplatesManager
import org.jetbrains.plugins.groovy.GroovyBundle
import java.io.IOException

@Service(Service.Level.APP)
@State(name = "GroovyToStringTemplates", storages = [Storage("groovyToStringTemplates.xml")], category = SettingsCategory.CODE)
internal class GroovyToStringTemplatesManager : TemplatesManager() {
  override fun getDefaultTemplates(): List<TemplateResource?> = listOf(
    TemplateResource(GroovyBundle.message("generate.tostring.template.interpolation"), readFile(DEFAULT_CONCAT_GROOVY), true),
    TemplateResource(GroovyBundle.message("generate.tostring.template.interpolation.with.super"), readFile(DEFAULT_CONCAT_SUPER_GROOVY), true)
  )

  companion object {
    fun getInstance(): GroovyToStringTemplatesManager = ApplicationManager.getApplication().getService(GroovyToStringTemplatesManager::class.java)

    private const val DEFAULT_CONCAT_GROOVY = "/org/jetbrains/plugins/groovy/generate/template/toString/DefaultConcatMemberGroovy.vm"
    private const val DEFAULT_CONCAT_SUPER_GROOVY = "/org/jetbrains/plugins/groovy/generate/template/toString/DefaultConcatMemberSuperGroovy.vm"

    @Throws(IOException::class)
    private fun readFile(resource: String?): String {
      return readFile(resource, GroovyToStringTemplatesManager::class.java)
    }
  }
}
