// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons

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
