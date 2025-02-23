// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.webSymbols.references.impl

import com.intellij.diagnostic.PluginException
import com.intellij.lang.Language
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.references.PsiWebSymbolReferenceProvider

class PsiWebSymbolReferenceProviderBean : CustomLoadingExtensionPointBean<PsiWebSymbolReferenceProvider<*>>() {
  /**
   * [id][Language.getID] of the language for which references are provided.<br></br>
   * The references will be provided for the specified language and its [base languages][Language.getBaseLanguage].
   */
  @Attribute
  @RequiredElement(allowEmpty = true)
  var hostLanguage: String? = null

  /**
   * Fully qualified name of the class of the PsiElement for which references are provided.<br></br>
   * The references will be provided for the specified class and its superclasses.
   */
  @Attribute
  @RequiredElement
  var hostElementClass: String? = null

  @Attribute
  @RequiredElement
  var implementationClass: String? = null

  override fun getImplementationClassName(): String? {
    return implementationClass
  }

  fun getHostLanguage(): Language {
    val language = Language.findLanguageByID(hostLanguage)
    if (language == null) {
      throw PluginException("Cannot find language '$hostLanguage'", pluginDescriptor.getPluginId())
    }
    return language
  }

  fun getHostElementClass(): Class<out PsiExternalReferenceHost> {
    return loadClass<PsiExternalReferenceHost>(hostElementClass!!)
  }

  private fun <T> loadClass(fqn: String): Class<T> {
    val pluginDescriptor = getPluginDescriptor()
    try {
      @Suppress("UNCHECKED_CAST")
      return Class.forName(fqn, true, pluginDescriptor.getPluginClassLoader()) as Class<T>
    }
    catch (e: ClassNotFoundException) {
      throw PluginException(e, pluginDescriptor.getPluginId())
    }
  }
}
