// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ArrayUtil


@Suppress("EXPOSED_PACKAGE_PRIVATE_TYPE_FROM_INTERNAL_WARNING") // EditorAndState is package-private
internal data class UndoCommandData(
  val project: Project?,
  val undoConfirmationPolicy: UndoConfirmationPolicy,
  val editorStateBefore: EditorAndState?,
  val editorStateAfter: EditorAndState?,  // mutable
  private val originalDocument: DocumentReference?,  // mutable
  val undoableActions: Collection<UndoableAction>,  // mutable
  val allAffectedDocuments: Collection<DocumentReference>,  // mutable
  val additionalAffectedDocuments: Collection<DocumentReference>,  // mutable
  val isForcedGlobal: Boolean,  // mutable
  private val isTransparent: Boolean,
  private val isTransparentSupported: Boolean,
  val isValid: Boolean, // mutable
) {

  companion object {
    @JvmField
    val NO_COMMAND: UndoCommandData = UndoCommandData(
      null,
      UndoConfirmationPolicy.DEFAULT,
      null,
      null,
      null,
      emptyList(),
      emptyList(),
      emptyList(),
      false,
      false,
      true,
      false,
    )
  }

  fun shouldClearRedoStack(): Boolean {
    // we do not want to spoil redo stack in situation, when some 'transparent' actions occurred right after undo.
    return !isTransparent() && hasActions()
  }

  fun hasActions(): Boolean {
    return undoableActions.isNotEmpty()
  }

  fun isTransparent(): Boolean {
    if (isTransparentSupported) {
      return isTransparent
    }
    return isTransparent && !hasActions()
  }

  fun isGlobal(): Boolean {
    return isForcedGlobal || affectsMultiplePhysicalDocs()
  }

  fun withOriginalDocument(): UndoCommandData {
    return if (originalDocument != null && hasActions() && !isTransparent() && isPhysical()) {
      withAffectedDocument(originalDocument)
    } else {
      this
    }
  }

  fun withAction(action: UndoableAction): UndoCommandData {
    val newIsGlobal = isForcedGlobal || action.isGlobal
    val newUndoableActions = buildList {
      addAll(undoableActions)
      add(action)
    }
    val newAllAffectedDocuments = action.affectedDocuments?.let { affectedByAction ->
      buildSet {
        addAll(allAffectedDocuments)
        addAll(affectedByAction)
      }
    } ?: allAffectedDocuments
    return copy(
      undoableActions=newUndoableActions,
      allAffectedDocuments=newAllAffectedDocuments,
      isForcedGlobal=newIsGlobal,
    )
  }

  fun withAdditionalAffectedDocuments(refs: Collection<DocumentReference>): UndoCommandData {
    return if (refs.isEmpty()) {
      this
    } else {
      val newAdditionalAffectedDocuments = buildSet {
        addAll(additionalAffectedDocuments)
        addAll(refs)
      }
      copy(additionalAffectedDocuments=newAdditionalAffectedDocuments)
    }
  }

  fun withAffectedDocument(ref: DocumentReference): UndoCommandData {
    return if (hasChangesOf(ref)) {
      this
    } else {
      withAction(MentionOnlyUndoableAction(arrayOf(ref)))
    }
  }

  fun withEditorStateAfter(editorStateAfter: EditorAndState?): UndoCommandData {
    return copy(editorStateAfter=editorStateAfter)
  }

  fun withOriginalDocument(documentFromEditor: DocumentReference?): UndoCommandData {
    return copy(originalDocument=documentFromEditor)
  }

  fun invalidIfAffects(ref: DocumentReference): UndoCommandData {
    return if (allAffectedDocuments.contains(ref)) {
      copy(isValid=false)
    } else {
      this
    }
  }

  fun markGlobal(): UndoCommandData {
    return copy(isForcedGlobal=true)
  }

  private fun affectsMultiplePhysicalDocs(): Boolean {
    val affectedFiles = HashSet<VirtualFile>()
    for (ref in allAffectedDocuments) {
      val file = ref.getFile()
      if (file != null && !isVirtualDocumentChange(file)) {
        affectedFiles.add(file)
        if (affectedFiles.size > 1) {
          return true
        }
      }
    }
    return false
  }

  private fun hasChangesOf(ref: DocumentReference): Boolean {
    for (action in undoableActions) {
      val affected: Array<DocumentReference>? = action.getAffectedDocuments()
      if (affected != null) {
        if (ArrayUtil.contains(ref, *affected)) {
          return true
        }
      }
    }
    return hasActions() && additionalAffectedDocuments.contains(ref)
  }

  private fun isPhysical(): Boolean {
    if (allAffectedDocuments.isEmpty()) {
      return false
    }
    for (ref in allAffectedDocuments) {
      if (isVirtualDocumentChange(ref.getFile())) {
        return false
      }
    }
    return true
  }

  private fun isVirtualDocumentChange(file: VirtualFile?): Boolean {
    return file == null || file is LightVirtualFile
  }
}
