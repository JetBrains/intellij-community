// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.common.formatting

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.formatting.*
import com.intellij.psi.codeStyle.CodeStyleSettings

class EditorConfigFormattingModelBuilder : FormattingModelBuilder {
  override fun createModel(formattingContext: FormattingContext): FormattingModel {
    val settings = formattingContext.codeStyleSettings
    val spacingBuilder = createSpacingBuilder(settings)
    val commonSettings = settings.getCommonSettings(EditorConfigLanguage)
    val shouldAlignSeparators = commonSettings.ALIGN_GROUP_FIELD_DECLARATIONS
    val root = EditorConfigFormattingBlock(formattingContext.node, spacingBuilder, shouldAlignSeparators)
    return FormattingModelProvider.createFormattingModelForPsiFile(formattingContext.containingFile, root, settings)
  }

  private fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
    val commonSettings = settings.getCommonSettings(EditorConfigLanguage)
    val beforeComma = commonSettings.SPACE_BEFORE_COMMA
    val afterComma = commonSettings.SPACE_AFTER_COMMA
    val beforeColon = commonSettings.SPACE_BEFORE_COLON
    val afterColon = commonSettings.SPACE_AFTER_COLON
    val aroundSeparator = commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS

    return SpacingBuilder(settings, EditorConfigLanguage)
      .around(EditorConfigElementTypes.SEPARATOR).strictSpaces(aroundSeparator)
      .before(EditorConfigElementTypes.COMMA).strictSpaces(beforeComma)
      .after(EditorConfigElementTypes.COMMA).strictSpaces(afterComma)
      .around(EditorConfigElementTypes.DOT).strictSpaces(false)
      .before(EditorConfigElementTypes.COLON).strictSpaces(beforeColon)
      .after(EditorConfigElementTypes.COLON).strictSpaces(afterColon)
      .around(EditorConfigElementTypes.LINE_COMMENT).spaces(1)
      .around(EditorConfigElementTypes.OPTION).lineBreakInCode()
      .around(EditorConfigElementTypes.ROOT_DECLARATION).blankLines(1)
      .around(EditorConfigElementTypes.SECTION).blankLines(1)
  }

  private fun SpacingBuilder.RuleBuilder.strictSpaces(needSpace: Boolean) = spacing(needSpace.toInt(), needSpace.toInt(), 0, false, 0)
  private fun Boolean.toInt() = if (this) 1 else 0
}
