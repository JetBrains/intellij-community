package com.intellij.cce.actions

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.processor.CallCompletionProcessor
import com.intellij.cce.processor.CompletionGolfProcessor
import com.intellij.cce.processor.DeleteScopesProcessor
import com.intellij.cce.util.FileTextUtil.computeChecksum

class ActionsGenerator(val strategy: CompletionStrategy, private val language: Language) {

  fun generate(code: CodeFragment): FileActions {
    val deletionVisitor = DeleteScopesProcessor()
    if (strategy.context == CompletionContext.PREVIOUS) deletionVisitor.process(code)

    val completionVisitor =
      if (strategy.completionGolf != null) CompletionGolfProcessor()
      else CallCompletionProcessor(code.text, strategy, language, code.offset)

    completionVisitor.process(code)

    val actions: MutableList<Action> = mutableListOf()
    val completionActions = completionVisitor.getActions()
    if (completionActions.isNotEmpty()) {
      actions.addAll(deletionVisitor.getActions().reversed())
      actions.addAll(completionActions)
    }
    return FileActions(code.path, computeChecksum(code.text),
                       actions.count { it is FinishSession || it is EmulateUserSession || it is CompletionGolfSession }, actions)
  }
}
