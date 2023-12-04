// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.lang

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.XMap
import training.util.*

@State(name = "LangManager", storages = [
  Storage(value = StoragePathMacros.NON_ROAMABLE_FILE),
  Storage(value = trainerPluginConfigName, deprecated = true)
])
class LangManager : SimplePersistentStateComponent<LangManager.State>(State()) {
  val supportedLanguagesExtensions: List<LangSupportBean>
    get() {
      return ExtensionPointName<LangSupportBean>(LangSupport.EP_NAME).extensionList
        .filter { courseCanBeUsed(it.getLang()) }
    }

  val languages: List<LangSupportBean>
    get() = supportedLanguagesExtensions.filter { Language.findLanguageByID(it.language) != null }

  private val langSupportDelegator = LazyWeakReferenceDelegator {
    supportedLanguagesExtensions.find { langBean -> langBean.language == state.languageName }?.instance
  }
  private val langSupportRef: LangSupport? by langSupportDelegator

  init {
    installDefaultLangSupport()
  }

  companion object {
    fun getInstance() = service<LangManager>()
  }

  fun getLearningProjectPath(languageId: String): String? = state.languageToProjectMap[languageId]

  fun setLearningProjectPath(langSupport: LangSupport, path: String) {
    if (!langSupport.useUserProjects) {
      state.languageToProjectMap[langSupport.primaryLanguage] = path
      state.intIncrementModificationCount()
    }
  }

  fun getLangSupportById(languageId: String): LangSupport? {
    return supportedLanguagesExtensions.singleOrNull { it.language == languageId }?.instance
  }

  // do not call this if LearnToolWindow with modules or learn views due to reinitViews
  fun updateLangSupport(languageId: String) {
    val oldLanguage = state.languageName
    state.languageName = supportedLanguagesExtensions.find { it.language == languageId }?.language
                         ?: throw Exception("Unable to find LangSupport for language: $languageId")
    if (state.languageName != oldLanguage) {
      langSupportDelegator.reset()
    }
    getAllLearnToolWindows().forEach { it.reinitViews() }
  }

  fun getLangSupport(): LangSupport? = langSupportRef

  fun getLanguageId(): String? = state.languageName

  fun getLangSupportBean(): LangSupportBean? {
    return supportedLanguagesExtensions.find { langBean -> langBean.language == state.languageName }
  }

  override fun loadState(state: State) {
    val oldLanguage = this.state.languageName
    super.loadState(state)
    // reset to default lang if stored lang is not present (for example, if plugin with this lang is disabled)
    if (getLangSupportBean() == null) {
      installDefaultLangSupport()
    }
    if (oldLanguage != state.languageName) {
      langSupportDelegator.reset()
    }
  }

  fun getLanguageDisplayName(): String {
    val default = "default"
    val languageName = state.languageName ?: return default
    return (findLanguageByID(languageName) ?: return default).displayName
  }

  private fun installDefaultLangSupport() {
    val productName = ApplicationNamesInfo.getInstance().productName
    val langSupportBeans = languages
    val defaultLang = langSupportBeans.singleOrNull()
                      ?: langSupportBeans.singleOrNull { it.defaultProductName?.split(",")?.contains(productName) ?: false }
                      ?: langSupportBeans.firstOrNull()?.also {
                        if (!ApplicationManager.getApplication().isUnitTestMode) {
                          logger<LangManager>().warn("No default language for $productName. Selected ${it.language}.")
                        }
                      }
    if (defaultLang != null) {
      state.languageName = defaultLang.language
    }
  }

  // Note: languageName - it is language Id actually
  class State : BaseState() {
    var languageName by string()

    @get:XMap
    val languageToProjectMap by linkedMap<String, String>()
  }
}