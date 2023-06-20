// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion


import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.common.BasicActionsInvoker
import com.intellij.cce.evaluation.CodeCompletionHandlerFactory
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

abstract class BaseCompletionActionsInvoker(project: Project,
                                            language: Language) : BasicActionsInvoker(project, language) {

  protected fun invokeCompletion(expectedText: String,
                                 prefix: String?,
                                 completionType: com.intellij.codeInsight.completion.CompletionType): LookupEx? {
    val handlerFactory = CodeCompletionHandlerFactory.findCompletionHandlerFactory(project, language)
    val handler = handlerFactory?.createHandler(completionType, expectedText, prefix) ?: object : CodeCompletionHandlerBase(completionType,
                                                                                                                            false, false,
                                                                                                                            true) {
      // Guarantees synchronous execution
      override fun isTestingCompletionQualityMode() = true
      override fun lookupItemSelected(indicator: CompletionProgressIndicator?,
                                      item: LookupElement,
                                      completionChar: Char,
                                      items: MutableList<LookupElement>?) {
        afterItemInsertion(indicator, null)
      }
    }
    try {
      handler.invokeCompletion(project, editor)
    }
    catch (e: AssertionError) {
      LOG.warn("Completion invocation ended with error", e)
    }
    return LookupManager.getActiveLookup(editor)
  }

  protected fun LookupImpl.finish(expectedItemIndex: Int, completionLength: Int, forceUndo: Boolean = false): Boolean {
    selectedIndex = expectedItemIndex
    val document = editor.document
    val lengthBefore = document.textLength
    try {
      finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR, items[expectedItemIndex])
    }
    catch (e: Throwable) {
      LOG.warn("Lookup finishing error.", e)
      return false
    }
    if (forceUndo || lengthBefore + completionLength != document.textLength) {
      LOG.info("Undo operation after finishing completion.")
      UndoManagerImpl.getInstance(project).undo(FileEditorManager.getInstance(project).selectedEditor)
      return false
    }
    return true
  }
}
