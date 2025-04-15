// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigQualifiedKeyPart
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

abstract class EditorConfigOptionBase(node: ASTNode) : EditorConfigDescribableElementBase(node), EditorConfigOption {
  override fun getPresentation(): ItemPresentation? {
    return PresentationData(name, declarationSite, IconManager.getInstance().getPlatformIcon(PlatformIcons.Property), null)
  }

  override fun getName(): String {
    return keyParts.joinToString(separator = ".")
  }

  final override fun getKeyParts(): List<String> =
    flatOptionKey?.text?.let(::listOf)
    ?: qualifiedOptionKey?.qualifiedKeyPartList?.map(EditorConfigQualifiedKeyPart::getText)
    ?: emptyList()

  final override fun getAnyValue(): EditorConfigDescribableElement? =
    optionValueIdentifier
    ?: optionValueList
    ?: optionValuePair
}
