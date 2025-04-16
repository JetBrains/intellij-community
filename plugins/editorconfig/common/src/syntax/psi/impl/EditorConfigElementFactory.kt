package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.*
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
