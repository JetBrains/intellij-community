package com.intellij.grazie.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property

data class CheckingContext(
  @Property val isCheckInCommitMessagesEnabled: Boolean = true,
  @Property val isCheckInStringLiteralsEnabled: Boolean = false,
  @Property val isCheckInCommentsEnabled: Boolean = true,
  @Property val isCheckInDocumentationEnabled: Boolean = true,

  /** The IDs of the programming languages where the grammar checking is explicitly disabled by the user */
  @Property val disabledLanguages: Set<String> = HashSet(),

  /** The IDs of the programming languages where the grammar checking is disabled by default but explicitly enabled by the user */
  @Property val enabledLanguages: Set<String> = HashSet()
) {

  fun getEffectivelyDisabledLanguageIds(): Set<String> {
    return disabledLanguages + getLanguagesDisabledByDefault().filter { it !in enabledLanguages }
  }

  fun domainsDiffer(another: CheckingContext): Boolean =
    another.isCheckInCommitMessagesEnabled != isCheckInCommitMessagesEnabled ||
    another.isCheckInCommentsEnabled != isCheckInCommentsEnabled ||
    another.isCheckInStringLiteralsEnabled != isCheckInStringLiteralsEnabled ||
    another.isCheckInDocumentationEnabled != isCheckInDocumentationEnabled

  fun languagesDiffer(another: CheckingContext): Boolean =
    another.disabledLanguages != disabledLanguages || another.enabledLanguages != enabledLanguages

  companion object {
    private val extensionPoint = ApplicationManager.getApplication().extensionArea
      .getExtensionPoint<DisableChecking>("com.intellij.grazie.disableChecking")

    fun getLanguagesDisabledByDefault(): Set<String> = extensionPoint.extensionList.map { it.language }.toSet()
  }

  @Property(style = Property.Style.ATTRIBUTE)
  internal class DisableChecking {
    @RequiredElement
    @Attribute
    var language = ""
  }
}