// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.toml

import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlPsiFactory

internal class GradleTomlSuppressByCommentFix(toolId: String): SuppressByCommentFix(toolId, TomlKeySegment::class.java) {
  override fun createSuppression(project: Project, element: PsiElement, container: PsiElement) {
    if (container !is TomlKeySegment) return
    val tomlKeyValue = getTomlKeyValue(container) ?: return
    // The super call inserts a suppression comment but doesn't add a new line after it.
    super.createSuppression(project, element, tomlKeyValue)
    val psiNewLine = TomlPsiFactory(project).createNewline()
    tomlKeyValue.parent.addBefore(psiNewLine, tomlKeyValue)
  }

  private fun getTomlKeyValue(keySegment: TomlKeySegment): TomlKeyValue? {
    val tomlKey = keySegment.parent.asSafely<TomlKey>() ?: return null
    return tomlKey.parent.asSafely<TomlKeyValue>()
  }
}