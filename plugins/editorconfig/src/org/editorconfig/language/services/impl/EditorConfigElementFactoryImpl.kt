// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.filetype.EditorConfigFileType
import org.editorconfig.language.psi.*
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.services.EditorConfigElementFactory

class EditorConfigElementFactoryImpl(private val project: Project) : EditorConfigElementFactory {
  private fun createDummyFile(content: CharSequence) =
    PsiFileFactory.getInstance(project)
      .createFileFromText(EditorConfigFileConstants.FILE_NAME, EditorConfigFileType, content)
      as EditorConfigPsiFile

  override fun createRootDeclaration(file: PsiFile) =
    createDummyFile(EditorConfigFileConstants.getRootDeclarationFor(file))
      .firstChild as EditorConfigRootDeclaration

  override fun createSection(source: CharSequence) =
    createDummyFile(source)
      .sections
      .single()

  override fun createHeader(source: CharSequence) =
    createSection(source).header

  override fun createPattern(source: CharSequence) =
    createSection("[$source]")
      .header
      .pattern!!

  override fun createCharClassPattern(source: CharSequence) =
    PsiTreeUtil.findChildOfType(createPattern(source), EditorConfigCharClassPattern::class.java, false)!!

  override fun createAnyValue(source: CharSequence) =
    createOption("foo=$source").anyValue!!

  override fun createValueIdentifier(source: CharSequence) =
    createOption("foo=$source").optionValueIdentifier!!

  override fun createOption(source: CharSequence) =
    createSection("[*]$source")
      .optionList
      .single<EditorConfigOption>()

  override fun createKey(source: CharSequence): EditorConfigDescribableElement {
    val option = createOption("$source=bar")
    return option.flatOptionKey ?: option.qualifiedOptionKey ?: throw IllegalStateException()
  }

  override fun createKeyPart(source: CharSequence) =
    createOption("hello.$source=value")
      .qualifiedOptionKey!!
      .qualifiedKeyPartList[1]!!
}
