// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
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
  val supportedLanguagesExtensions: List<LanguageExtensionPoint<LangSupport>>
    get() {
      return ExtensionPointName<LanguageExtensionPoint<LangSupport>>(LangSupport.EP_NAME).extensionList
        .filter { courseCanBeUsed(it.language) }
    }

  val languages: List<LanguageExtensionPoint<LangSupport>>
    get() = supportedLanguagesExtensions.filter { Language.findLanguageByID(it.language) != null }

  private var langSupportRef: LangSupport? by WeakReferenceDelegator()

  init {
    val productName = ApplicationNamesInfo.getInstance().productName
    val onlyLang =
      languages.singleOrNull()
      ?: languages.singleOrNull { it.instance.defaultProductName == productName }
      ?: languages.firstOrNull()?.also {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          logger<LangManager>().warn("No default language for $productName. Selected ${it.language}.")
        }
      }

    if (onlyLang != null) {
      langSupportRef = onlyLang.instance
      state.languageName = onlyLang.language
    }
  }

  companion object {
    fun getInstance() = service<LangManager>()
  }

  fun getLearningProjectPath(langSupport: LangSupport): String? =
    if (langSupport.useUserProjects) null
    else state.languageToProjectMap[langSupport.primaryLanguage]

  fun setLearningProjectPath(langSupport: LangSupport, path: String) {
    state.languageToProjectMap[langSupport.primaryLanguage] = path
    state.intIncrementModificationCount()
  }

  fun getLangSupportById(languageId: String): LangSupport? {
    return supportedLanguagesExtensions.singleOrNull { it.language == languageId }?.instance
  }

  // do not call this if LearnToolWindow with modules or learn views due to reinitViews
  fun updateLangSupport(langSupport: LangSupport) {
    this.langSupportRef = langSupport
    state.languageName = supportedLanguagesExtensions.find { it.instance == langSupport }?.language
                         ?: throw Exception("Unable to get language.")
    getAllLearnToolWindows().forEach { it.reinitViews() }
  }

  fun getLangSupport(): LangSupport? = langSupportRef

  override fun loadState(state: State) {
    super.loadState(state)
    langSupportRef = supportedLanguagesExtensions.find { langExt -> langExt.language == state.languageName }?.instance ?: return
  }

  fun getLanguageDisplayName(): String {
    val default = "default"
    val languageName = state.languageName ?: return default
    return (findLanguageByID(languageName) ?: return default).displayName
  }

  // Note: languageName - it is language Id actually
  class State : BaseState() {
    var languageName by string()

    @get:XMap
    val languageToProjectMap by linkedMap<String, String>()
  }
}