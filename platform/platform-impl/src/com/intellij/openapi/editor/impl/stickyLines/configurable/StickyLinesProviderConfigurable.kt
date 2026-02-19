// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.configurable

import com.intellij.lang.Language
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox

internal class StickyLinesProviderConfigurable(
  private val provider: BreadcrumbsProvider,
  private val language: Language,
) : SearchableConfigurable {

  override fun createComponent(): JCheckBox = JCheckBox(language.displayName)
    .apply { isOpaque = false }

  override fun isModified(): Boolean = false

  override fun apply(): Unit = Unit

  @Nls(capitalization = Nls.Capitalization.Title)
  override fun getDisplayName(): String = language.displayName

  override fun getId(): String = language.id

  override fun getOriginalClass(): Class<*> = provider.javaClass
}
