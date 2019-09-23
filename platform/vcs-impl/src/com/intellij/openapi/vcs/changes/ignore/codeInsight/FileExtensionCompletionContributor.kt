// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.codeInsight

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * File extension completion by mask e.g. "*.*", "*.txt" etc.
 */
class FileExtensionCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               val current = parameters.position
               if (completionSupported(current.text)) {
                 result.addAllElements(
                   FileTypeRegistry.getInstance().registeredFileTypes.map { it.defaultExtension to it.icon }.map { (extension, icon) ->
                     LookupElementBuilder.create(extension).withIcon(icon)
                   }
                 )
               }
             }
           }
    )
  }

  companion object {
    private const val EXTENSION_MASK = "*."

    fun completionSupported(text: String): Boolean {
      return text.startsWith(EXTENSION_MASK) || text.contains("/$EXTENSION_MASK")
    }
  }
}