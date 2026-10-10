// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCompositeCommand
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModMoveFile
import com.intellij.modcommand.ModNothing
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPointerManager.getInstance
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer

/**
 * The result of [ModPlanRefactoring.plan]: a [RefusedPlan], or what the refactoring would do and the execution that does it.
 *
 * The PSI elements of a plan are physical and valid only inside the read action that built the plan.
 */
sealed interface ModPlan {
  /**
   * Why a ModPlan refactoring cannot run at all: the plan of a refactoring that can do nothing.
   *
   * @property location the place of the element that caused the refusal; null if the element is gone
   */
  class RefusedPlan(val reason: ModPlanAnalysisResult.RefusedReason, val location: SmartPsiFileRange?) : ModPlan {
    /**
     * @param element the element that caused the refusal; null if it is gone
     */
    @RequiresReadLock
    constructor(reason: ModPlanAnalysisResult.RefusedReason, element: PsiElement?) :
      this(reason, element?.createSmartFileRange())
  }

  /**
   * @property conflicts the conflicts of the refactoring, and the non-code usages of the changed elements, such as comments and strings;
   * they are never edited, see [ModPlanConflicts.nonCodeUsages]
   * @property execution what [ModPlanExecutor] runs; its [ModPlanExecution.affectedFiles] are the files that change
   */
  class AcceptedPlan(val conflicts: ModPlanConflicts, val execution: ModPlanExecution) : ModPlan

  companion object {
    /**
     * The plan of [edits] with [conflicts]: [toCommand] converts the edits into the writes of the execution. Called in the analysis read
     * action.
     *
     * @param edits the edits of the refactoring, which run in the given order, see [toCommand]
     * @param eventElements the elements that the refactoring events report
     * @param anchor the element the refactoring was invoked on, see [toCommand]
     * @param trashDeletedFiles whether [ModPlanExecutor] moves the deleted files to the trash if the user deletes to it in the settings.
     * A directory is always deleted.
     * @return a [AcceptedPlan], or a [ModPlanAnalysisResult.RefusedReason.EDIT_FAILED] refusal if an edit failed, see [toCommand]
     */
    @RequiresReadLock
    fun createPlan(edits: List<ModPlanEdit>, conflicts: ModPlanConflicts, eventElements: List<PsiElement>,
                   commandName: @NlsContexts.Command String, refactoringId: @NonNls String, anchor: PsiElement,
                   trashDeletedFiles: Boolean): ModPlan {
      val project = anchor.project
      val command = toCommand(edits, anchor)
                    ?: return RefusedPlan(ModPlanAnalysisResult.RefusedReason.EDIT_FAILED, anchor)
      val fileWrites = command.unpack().filterNot { it is ModNothing || it is ModUpdateFileText && it.isEmpty }
      val stamps = fileWrites.filter { it is ModDeleteFile || it is ModMoveFile }.map { ModPlanExecution.writtenFile(it) }
        .associateWith { ModPlanExecution.stampOf(it) }
      val smartPointerManager = getInstance(project)
      val execution = ModPlanExecution(
        project, commandName, refactoringId, fileWrites, stamps,
        eventElements.map { smartPointerManager.createSmartPsiElementPointer(it) }, trashDeletedFiles,
      )
      return AcceptedPlan(conflicts, execution)
    }

    /** A write on the copies of [elements], which the engine hands to [write] in the same order. */
    private class PsiWrite(val elements: List<PsiElement>, val write: Consumer<in List<PsiElement>>)

    /**
     * Converts [edits] into one [ModCommand] on non-physical copies: the PSI edits run in their order in one updater, then the
     * [ModPlanRefactoringListener]s run on the changed files; the file edits follow as file writes. An edit inside a deleted file is
     * skipped.
     *
     * @param anchor the element the refactoring was invoked on; the command is built for its file, or for the file of the first edit that
     * has one
     * @return the file writes as one command; null if an edit cannot be written
     */
    @RequiresReadLock
    private fun toCommand(edits: List<ModPlanEdit>, anchor: PsiElement): ModCommand? {
      val deletedFiles = edits.filterIsInstance<ModPlanEdit.Delete>().map { it.file() }
      if (deletedFiles.any { !it.isValid }) return null
      val writtenFiles = HashSet<VirtualFile>()
      val fileWrites = ArrayList<ModCommand>()
      val psiWrites = ArrayList<PsiWrite>()
      for (edit in edits) {
        when (edit) {
          is ModPlanEdit.Update -> {
            val write = mapUpdate(edit) ?: return null
            if (!write.elements.all { isInsideDeletedFile(PsiUtilCore.getVirtualFile(it), it is PsiFileSystemItem, deletedFiles) }) {
              psiWrites.add(write)
            }
          }
          is ModPlanEdit.Delete -> {
            if (!isInsideDeletedFile(edit.file(), true, deletedFiles) && writtenFiles.add(edit.file())) {
              fileWrites.add(ModDeleteFile(edit.file()))
            }
          }
          is ModPlanEdit.Create -> {
            val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(edit.name())
            fileWrites.add(ModCreateFile(FutureVirtualFile(edit.parent(), edit.name(), fileType), ModCreateFile.Text(edit.text())))
          }
          is ModPlanEdit.Move -> {
            val fileType = if (edit.file().isDirectory) {
              null
            }
            else {
              edit.file().fileType
            }
            fileWrites.add(ModMoveFile(edit.file(), FutureVirtualFile(edit.targetDirectory(), edit.newName(), fileType)))
          }
        }
      }

      val anchorFile =
        anchor.containingFile ?: psiWrites.firstNotNullOfOrNull { write -> write.elements.firstNotNullOfOrNull { it.containingFile } }
      val psiCommand = psiCommand(psiWrites, anchorFile) ?: return null
      return fileWrites.fold(psiCommand) { command, nextCommand -> command.andThen(nextCommand) }
    }

    /**
     * @return the update as the engine writes it; null if an element is gone, has no file, or is a light element whose source is of
     * another class
     */
    private fun mapUpdate(edit: ModPlanEdit.Update): PsiWrite? {
      val liveElements = edit.pointers().mapNotNull { it.element }
      if (liveElements.size != edit.pointers().size) return null
      val elements = liveElements.map { sourceOf(it) }
      if (elements.any { it !is PsiDirectory && it.containingFile == null } ||
          liveElements.zip(elements).any { [lightElement, source] -> lightElement.javaClass != source.javaClass }) {
        return null
      }
      return PsiWrite(elements, edit.action())
    }

    /** @return nop if there is nothing to write in PSI; null if there is, and no file to build the command for */
    private fun psiCommand(writes: List<PsiWrite>, anchor: PsiFile?): ModCommand? {
      if (writes.isEmpty()) return ModCommand.nop()
      if (anchor == null) return null
      return ModCommand.psiUpdate(ActionContext.from(null, anchor)) { updater ->
        val copies = writes.map { write -> write.elements.map { updater.getWritable(it) } }
        val files = copies.flatten().mapNotNull { it.containingFile }.distinct()
        keepIndentOfFiles(updater, copies.flatten())
        writes.forEachIndexed { index, write ->
          // Another edit may already have removed the element, such as a usage that deletes its parent or an edit that replaces the
          // deleted element: there is nothing left to write.
          if (copies[index].all { it.isValid }) {
            write.write.accept(copies[index])
          }
        }
        for (file in files) {
          if (file.isValid) {
            ModPlanRefactoringListener.runAfterEdits(file)
          }
        }
      }
    }

    /**
     * The editor detects the indent of a file and keeps it with the document, but it ignores a copy, which is not a physical file. Then the
     * formatter of an edit in the copy sets the indent of the style of the project, and the lines that it touches differ from the lines
     * around them. The copy takes the indent that the document of the file has.
     */
    private fun keepIndentOfFiles(updater: ModPsiUpdater, copies: List<PsiElement>) {
      for (copyFile in copies.mapNotNull { it.containingFile }.distinct()) {
        val originalFile = updater.getOriginalFile(copyFile)
        val options = originalFile.viewProvider.document?.let { IndentOptions.retrieveFromAssociatedDocument(it) } ?: continue
        copyFile.viewProvider.document?.let { options.associateWithDocument(it) }
      }
    }

    /** A light element is written through its source, as its own `delete` does; a physical element is written as it is. */
    private fun sourceOf(element: PsiElement): PsiElement {
      if (element.isPhysical) return element
      val source = element.navigationElement
      return if (source !== element && source.isPhysical) source else element
    }

    /**
     * Whether [file] is one of [deletedFiles] or is in one of them; with [strict], only whether it is in one of them. An edit inside a
     * deleted element is not checked here: the write skips it, because its copy is not valid after the element is deleted.
     */
    private fun isInsideDeletedFile(file: VirtualFile?, strict: Boolean, deletedFiles: List<VirtualFile>): Boolean {
      return file != null && deletedFiles.any { VfsUtilCore.isAncestor(it, file, strict) }
    }
  }
}

/**
 * The file writes of a refactoring, ready to be applied by [ModPlanExecutor]; [ModPlan.createPlan] converts the [ModPlanEdit]s of the
 * refactoring into them. It is built in the read action that analyzed the refactoring, so the executor can later detect that the files
 * changed since.
 *
 * @property commandName the name of the command that writes, for the undo and for a button that runs the refactoring
 * @property project the project of the writes, see [ModPlanExecutor]
 * @property refactoringId the id of the refactoring events
 * @property writes the writes, in the order of the edits, see [ModPlan.createPlan]
 * @property stamps the stamps of the files that are moved or deleted, as the analysis saw them, to detect a stale file, see [stampOf]
 * @property elements the elements that the events report
 * @property trashDeletedFiles whether the deleted files go to the trash, see [ModPlan.createPlan]
 */
class ModPlanExecution internal constructor(
  internal val project: Project,
  val commandName: @NlsContexts.Command String,
  internal val refactoringId: @NonNls String,
  internal val writes: List<ModCommand>,
  internal val stamps: Map<VirtualFile, Long>,
  internal val elements: List<SmartPsiElementPointer<PsiElement>>,
  internal val trashDeletedFiles: Boolean,
) {
  /** Every existing file that the execution writes, in the order of the writes; a created file is not one of them. */
  val affectedFiles: List<VirtualFile> = writes.filter { it !is ModCreateFile }.map { writtenFile(it) }.distinct()

  /**
   * The writes as one command: for a preview, for an interactive client, or to join with other changes. Run it through
   * [ModPlanExecutor] to get the refactoring events and one global undo step. This is the integration point for a refactoring that
   * a quick fix or an action returns.
   */
  val command: ModCommand = ModCompositeCommand(writes)

  companion object {
    /** The path of [file], also of a file that does not exist yet. */
    internal fun pathOf(file: VirtualFile): String {
      return if (file is FutureVirtualFile) {
        pathOf(file.parent) + "/" + file.name
      }
      else {
        file.path
      }
    }

    /** The stamp of the document of [file], which also moves with the unsaved edits; of the file if it has no document, as a directory. */
    internal fun stampOf(file: VirtualFile): Long =
      FileDocumentManager.getInstance().getDocument(file)?.modificationStamp ?: file.modificationStamp

    internal fun writtenFile(write: ModCommand): VirtualFile {
      return when (write) {
        is ModCreateFile -> write.file()
        is ModUpdateFileText -> write.file()
        is ModMoveFile -> write.file()
        is ModDeleteFile -> write.file()
        else -> throw IllegalArgumentException("Not a file write: $write")
      }
    }
  }
}

/**
 * What the provider of a language, such as the Java safe delete provider, found for one subject of a ModPlan refactoring. The engine
 * turns the results of all the subjects into one plan.
 */
sealed interface ModPlanAnalysisResult {
  /**
   * The edits and the conflicts that the provider found for one subject.
   *
   * @property edits the changes
   * @property conflicts the conflicts of the refactoring, and the non-code usages: the places that are not edited
   */
  @JvmRecord
  data class Found(val edits: @JvmSuppressWildcards List<ModPlanEdit>, val conflicts: ModPlanConflicts) : ModPlanAnalysisResult

  /**
   * The refactoring cannot run for the subject.
   *
   * @property reason the cause; never [RefusedReason.EDIT_FAILED]: only the engine sets it
   */
  @JvmRecord
  data class Refused(val reason: RefusedReason) : ModPlanAnalysisResult {
    init {
      require(reason != RefusedReason.EDIT_FAILED) { "Only the engine refuses with $reason" }
    }
  }

  /** Why a ModPlan refactoring cannot run. */
  enum class RefusedReason {
    /** The refactoring cannot be applied to the element. */
    NOT_APPLICABLE,

    /** The language of the element did not opt in to the ModPlan refactoring. */
    NOT_SUPPORTED,

    /** An edit could not be turned into a command. */
    EDIT_FAILED,
  }
}
