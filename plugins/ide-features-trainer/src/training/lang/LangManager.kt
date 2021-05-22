// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import training.ui.LearnToolWindowFactory
import training.util.WeakReferenceDelegator
import training.util.courseCanBeUsed
import training.util.findLanguageByID
import training.util.trainerPluginConfigName

@State(name = "LangManager", storages = [Storage(value = trainerPluginConfigName)])
class LangManager : PersistentStateComponent<LangManager.State> {

  val supportedLanguagesExtensions: List<LanguageExtensionPoint<LangSupport>>
    get() = ExtensionPointName<LanguageExtensionPoint<LangSupport>>(LangSupport.EP_NAME).extensions
      .filter { courseCanBeUsed(it.language) }
      .toList()

  val languages: List<LanguageExtensionPoint<LangSupport>>
    get() = supportedLanguagesExtensions.filter { Language.findLanguageByID(it.language) != null }

  private var myState = State(null)

  private var myLangSupport: LangSupport? by WeakReferenceDelegator()

  init {
    val productName = ApplicationNamesInfo.getInstance().productName
    val onlyLang =
      languages.singleOrNull() ?:
      languages.singleOrNull { it.instance.defaultProductName == productName } ?:
      languages.firstOrNull()?.also {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          logger<LangManager>().warn("No default language for $productName. Selected ${it.language}.")
        }
      }

    if (onlyLang != null) {
      myLangSupport = onlyLang.instance
      myState.languageName = onlyLang.language
    }
  }

  companion object {
    fun getInstance() = service<LangManager>()
  }

  fun getLearningProjectPath(langSupport: LangSupport): String? = state.languageToProjectMap[langSupport.primaryLanguage]
  fun setLearningProjectPath(langSupport: LangSupport, path: String) {
    state.languageToProjectMap[langSupport.primaryLanguage] = path
  }

  fun getLangSupportById(languageId: String): LangSupport? {
    return supportedLanguagesExtensions.singleOrNull { it.language == languageId }?.instance
  }

  fun isLangUndefined() = (myLangSupport == null)

  //do not call this if LearnToolWindow with modules or learn views due to reinitViews
  fun updateLangSupport(langSupport: LangSupport) {
    myLangSupport = langSupport
    myState.languageName = supportedLanguagesExtensions.find { it.instance == langSupport }?.language
                           ?: throw Exception("Unable to get language.")
    LearnToolWindowFactory.learnWindowPerProject.values.forEach { it.reinitViews() }
  }

  fun getLangSupport(): LangSupport? {
    return myLangSupport
  }

  override fun loadState(state: State) {
    myLangSupport = supportedLanguagesExtensions.find { langExt -> langExt.language == state.languageName }?.instance ?: return
    myState.languageName = state.languageName
    myState.languageToProjectMap = state.languageToProjectMap
  }

  override fun getState() = myState

  fun getLanguageDisplayName(): String {
    val default = "default"
    val languageName = myState.languageName ?: return default
    return (findLanguageByID(languageName) ?: return default).displayName
  }

  // Note: languageName - it is language Id actually
  data class State(var languageName: String? = null, var languageToProjectMap: MutableMap<String, String> = mutableMapOf())
}