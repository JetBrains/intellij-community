// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigSection

abstract class EditorConfigSectionBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigSection {
  final override fun containsKey(key: EditorConfigFlatOptionKey) = optionList
    .asSequence()
    .mapNotNull(EditorConfigOption::getFlatOptionKey)
    .any(key::definesSameOption)

  final override fun getName(): String = header.text

  final override fun getPresentation(): ItemPresentation {
    val fileName = containingFile.presentation?.presentableText ?: ".editorconfig"
    return PresentationData(header.text, fileName, AllIcons.Nodes.Package, null)
  }
}
