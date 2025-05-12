// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoableAction


internal class UndoUnit(
  private val command: String,
  private val actions: Collection<UndoableAction>,
  private val isGlobal: Boolean,
  private val isTransparent: Boolean,
  private val isTemporary: Boolean,
  private val isValid: Boolean,
  private val confirmationPolicy: UndoConfirmationPolicy,
  private val affectedDocuments: Collection<DocumentReference>,
  private val additionalAffectedDocuments: Collection<DocumentReference>,
) {

  companion object {

    @JvmStatic
    fun fromGroup(group: UndoableGroup): UndoUnit {
      return UndoUnit(
        command(group.commandName),
        ArrayList(group.actions),
        group.isGlobal,
        group.isTransparent,
        group.isTemporary,
        group.isValid,
        group.confirmationPolicy,
        group.affectedDocuments,
        emptyList(),
      )
    }

    @JvmStatic
    fun fromMerger(merger: CommandMerger): UndoUnit {
      return UndoUnit(
        command(merger.commandName),
        ArrayList(merger.currentActions),
        merger.isGlobal,
        merger.isTransparent,
        merger.isTransparent,
        merger.isValid,
        merger.undoConfirmationPolicy,
        ArrayList(merger.allAffectedDocuments),
        ArrayList(merger.additionalAffectedDocuments),
      )
    }

    private fun command(commandName: String?): String {
      return when {
        commandName == null -> "NULL"
        commandName.isEmpty() -> "EMPTY"
        else -> "'$commandName'"
      }
    }
  }

  override fun toString(): String {
    val actionsStr = actions.joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]",
    ) { action -> "($action)" }
    val isGlobalStr = if (isGlobal) " global" else ""
    val isTransparentStr = if (isTransparent) " transparent" else ""
    val isTemporaryStr = if (isTemporary) " temp" else ""
    val isValidStr = if (!isValid) " invalid" else ""
    val confirmationPolicyStr = if (confirmationPolicy != UndoConfirmationPolicy.DEFAULT) " $confirmationPolicy" else ""
    val docs = if (affectedDocuments.size > 1) " affected: ${printDocs(affectedDocuments)}" else ""
    val addDocs = if (additionalAffectedDocuments.size > 1) " additional: ${printDocs(additionalAffectedDocuments)}" else ""
    return "{$command with ${actions.size} actions: $actionsStr" +
           "$isGlobalStr$isTransparentStr$isTemporaryStr$isValidStr$confirmationPolicyStr$docs$addDocs}"
  }

  private fun printDocs(docs: Collection<DocumentReference>): String {
    return docs.joinToString(", ", "[", "]") { d ->
      d.document?.toString() ?: "null"
    }
  }
}
