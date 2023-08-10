// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.editorconfig.language.psi.*
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

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
