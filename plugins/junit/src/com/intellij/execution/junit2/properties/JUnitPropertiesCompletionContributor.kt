// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.properties

import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.TailTypeDecorator
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext

internal class JUnitPropertiesCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      psiElement(PropertyKeyImpl::class.java)
        .inFile(psiFile().withName(JUNIT_PLATFORM_PROPERTIES_CONFIG)),

      object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
          val file = parameters.originalFile

          val variants = getJUnitPlatformProperties(file).values

          val delimiterChar = PropertiesCodeStyleSettings.getInstance(parameters.editor.project).delimiter
          val defaultDelimiterType = TailTypes.charType(delimiterChar)

          result.addAllElements(variants.map {
            val builder = LookupElementBuilder.create(it.key)
              .withPsiElement(it.declaration.retrieve())
              .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Property))

            TailTypeDecorator.withTail(builder, defaultDelimiterType)
          })
        }
      }
    )
  }
}