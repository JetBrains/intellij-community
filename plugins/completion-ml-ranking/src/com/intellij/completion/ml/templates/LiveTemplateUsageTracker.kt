package com.intellij.completion.ml.templates

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * @see LiveTemplateUsageFeatureProvider
 */
@Service
class LiveTemplateUsageTracker {
  private val templatesUseCount: MutableMap<String, Int> = mutableMapOf()

  init {
    PropertiesComponent.getInstance().getList(USED_TEMPLATES)?.forEach { templatesUseCount[it] = 0 }
  }

  fun incUseCount(template: String) {
    val useCount = templatesUseCount[template]
    if (useCount == null) {
      templatesUseCount[template] = 1
      PropertiesComponent.getInstance().setList(USED_TEMPLATES, templatesUseCount.keys)
      return
    }
    templatesUseCount[template] = useCount + 1
  }

  fun getRecentUseCount(template: String): Int? {
    return templatesUseCount[template]
  }

  companion object {
    fun getInstance(): LiveTemplateUsageTracker = service()
    private const val USED_TEMPLATES: String = "completion.ml.templates.used"
  }
}