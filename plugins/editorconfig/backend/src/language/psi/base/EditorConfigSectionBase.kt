// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.editorconfig.language.psi.EditorConfigSection

abstract class EditorConfigSectionBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigSection {

  final override fun getName(): String = header.text

  final override fun getPresentation(): ItemPresentation {
    val fileName = containingFile.presentation?.presentableText ?: ".editorconfig"
    return PresentationData(header.text, fileName, IconManager.getInstance().getPlatformIcon(PlatformIcons.Package), null)
  }
}
