package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigCharClassPattern
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPattern
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart
import com.intellij.editorconfig.common.syntax.psi.EditorConfigRootDeclaration
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

interface EditorConfigElementFactory {
  fun createRootDeclaration(file: PsiFile): EditorConfigRootDeclaration
  fun createSection(source: CharSequence): EditorConfigSection
  fun createHeader(source: CharSequence): EditorConfigHeader
  fun createPattern(source: CharSequence): EditorConfigPattern
  fun createCharClassPattern(source: CharSequence): EditorConfigCharClassPattern
  fun createAnyValue(source: CharSequence): EditorConfigDescribableElement
  fun createValueIdentifier(source: CharSequence): EditorConfigOptionValueIdentifier
  fun createOption(source: CharSequence): EditorConfigOption
  fun createKey(source: CharSequence): EditorConfigDescribableElement
  fun createKeyPart(source: CharSequence): EditorConfigQualifiedKeyPart

  companion object {
    fun getInstance(project: Project): EditorConfigElementFactory {
      return project.getService(EditorConfigElementFactory::class.java)
    }
  }
}
