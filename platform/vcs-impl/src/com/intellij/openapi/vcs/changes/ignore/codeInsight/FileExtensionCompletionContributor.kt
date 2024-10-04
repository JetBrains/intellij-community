// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.codeInsight

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus

/**
 * File extension completion by mask e.g. "*.*", "*.txt" etc.
 */
@ApiStatus.Internal
class FileExtensionCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               val current = parameters.position
               if (fileExtensionCompletionSupported(current.text)) {
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
    internal const val EXTENSION_MASK = "*."
  }
}

internal fun fileExtensionCompletionSupported(text: String): Boolean {
  return text.startsWith(FileExtensionCompletionContributor.EXTENSION_MASK) ||
         text.contains("/${FileExtensionCompletionContributor.EXTENSION_MASK}")
}