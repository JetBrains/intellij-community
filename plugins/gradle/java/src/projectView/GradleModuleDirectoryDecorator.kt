// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleTextAttributes

class GradleModuleDirectoryDecorator : ProjectViewNodeDecorator {
  override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
    if (node !is PsiDirectoryNode) return
    val project = node.project ?: return
    if (!isGradleProject(project)) return

    val virtualFile = node.virtualFile ?: return
    val decoration = gradleDirectoryDecoration(virtualFile, project) ?: return

    if (decoration.appendModuleName) {
      if (Registry.`is`("ide.hide.real.module.name")) return
      val dirName = data.presentableText ?: virtualFile.name
      data.clearText()
      data.addText("$dirName ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      data.addText("[${decoration.shortName}]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
    else if (decoration.isSourceSet) {
      val nameFragment = data.coloredText.firstOrNull() ?: return
      data.clearText()
      @NlsSafe val trimmed = nameFragment.text.trim()
      data.addText(trimmed, SimpleTextAttributes.merge(nameFragment.attributes, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES))
    }
  }
}
