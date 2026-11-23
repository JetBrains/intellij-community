// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.lang.injection.InjectedLanguageManager
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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.modplan.ModPlan.Companion.toCommand
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.NonNls
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

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
   * @property message the explanation of the refusal for the user, as the language or the platform gives it; null if there is none
   */
  class RefusedPlan(
    val reason: ModPlanAnalysisResult.RefusedReason,
    val location: SmartPsiFileRange?,
    val message: @NlsContexts.DialogMessage String?,
  ) : ModPlan {
    /**
     * @param element the element that caused the refusal; null if it is gone
     */
    @RequiresReadLock
    constructor(reason: ModPlanAnalysisResult.RefusedReason, element: PsiElement?, message: @NlsContexts.DialogMessage String?) :
      this(reason, element?.createSmartFileRange(), message)
  }

  /**
   * @property conflicts the conflicts of the refactoring, and the non-code usages of the changed elements, such as comments and strings;
   * they are never edited, see [ModPlanConflicts.nonCodeUsages]
   * @property execution what [ModPlanExecutor] runs; its [ModPlanExecution.affectedFiles] are the files that change
   * @property usages the places that the execution changes, which a preview shows and the user may exclude, see
   * [ModPlanRefactoring.EXCLUDED_USAGES]; empty if the refactoring shows none
   * @property writeEvents what the write tells the listeners, see [ModPlanExecutor.execute]. It runs in the command of the writes, before
   * the writes, so it keeps smart pointers, not PSI.
   */
  class AcceptedPlan internal constructor(
    val conflicts: ModPlanConflicts,
    val execution: ModPlanExecution,
    val usages: List<SmartPsiFileRange>,
    val writeEvents: Supplier<ModPlanRefactoring.WriteEvent>,
  ) : ModPlan

  companion object {
    /**
     * The plan of [edits] with [conflicts]: [toCommand] converts the edits into the writes of the execution. The caller left out the
     * edits of the excluded usages, see [ModPlanRefactoring.EXCLUDED_USAGES]. Called in the analysis read action.
     *
     * @param edits the edits of the refactoring, see [toCommand]
     * @param usages the places that the edits change, see [AcceptedPlan.usages]
     * @return a [AcceptedPlan], or a [ModPlanAnalysisResult.RefusedReason.EDIT_FAILED] refusal if an edit failed, see [toCommand]
     */
    @JvmStatic
    @JvmOverloads
    @RequiresReadLock
    fun createPlan(edits: List<ModPlanEdit>, conflicts: ModPlanConflicts,
                   commandName: @NlsContexts.Command String, refactoringId: @NonNls String, anchor: PsiElement,
                   trashDeletedFiles: Boolean, usages: List<SmartPsiFileRange> = emptyList(),
                   writeEvents: Supplier<ModPlanRefactoring.WriteEvent> = Supplier { ModPlanRefactoring.WriteEvent.DEFAULT }): ModPlan {
      val command = toCommand(edits, anchor) ?: return RefusedPlan(ModPlanAnalysisResult.RefusedReason.EDIT_FAILED, anchor, null)
      val fileWrites = command.unpack().filterNot { it is ModNothing || it is ModUpdateFileText && it.isEmpty }.distinct()
      val execution = ModPlanExecution(anchor.project, commandName, refactoringId, fileWrites, trashDeletedFiles)
      return AcceptedPlan(conflicts, execution, usages, writeEvents)
    }

    /** A write that runs among the PSI writes, in the order of the edits, on the writable copies of [elements]. */
    private sealed interface OrderedWrite {
      val elements: List<PsiElement>
    }

    /** A write on the copies of [elements], which the engine hands to [write] in the same order. */
    private class PsiWrite(override val elements: List<PsiElement>, val write: Consumer<in List<PsiElement>>) : OrderedWrite

    /** A [ModPlanEdit.UpdateRange] as the engine writes it: [action] gets the copy of [file] and the range that [range] has then. */
    private class RangeWrite(val file: PsiFile, val range: TextRange, val action: BiConsumer<in PsiFile, in TextRange>) : OrderedWrite {
      override val elements: List<PsiElement> get() = listOf(file)
    }

    /** A [ModPlanEdit.ReplaceText] as the engine writes it: [range] of the copy of [file] gets [newText]. */
    private class TextWrite(val file: PsiFile, val range: TextRange, val newText: String)

    /**
     * Converts [edits] into one [ModCommand] on non-physical copies: the PSI edits run in their order in one updater, then the text
     * replacements, then the [ModPlanRefactoringListener]s run on the changed files; the file edits follow as file writes. An edit inside
     * a deleted file is skipped.
     *
     * @param anchor the element the refactoring was invoked on; the command is built for its file, or for the file of the first edit that
     * has one
     * @return the file writes as one command; null if an edit cannot be written
     */
    @RequiresReadLock
    private fun toCommand(edits: List<ModPlanEdit>, anchor: PsiElement): ModCommand? {
      val deletedFiles = edits.filterIsInstance<ModPlanEdit.Delete>().map { it.file() }
      if (deletedFiles.any { !it.isValid }) return null
      // A file that moves out of a deleted directory keeps its updates, as a file of a directory that the move leaves empty.
      val movedFiles = edits.filterIsInstance<ModPlanEdit.Move>().map { it.file() }
      val writtenFiles = HashSet<VirtualFile>()
      val fileWrites = ArrayList<ModCommand>()
      val psiWrites = ArrayList<OrderedWrite>()
      val textWrites = ArrayList<TextWrite>()
      for (edit in edits) {
        when (edit) {
          is ModPlanEdit.Update -> {
            val write = mapUpdate(edit) ?: return null
            if (!write.elements.all {
                val file = PsiUtilCore.getVirtualFile(it)
                isInsideFiles(file, it is PsiFileSystemItem, deletedFiles) && !isInsideFiles(file, false, movedFiles)
              }) {
              psiWrites.add(write)
            }
          }
          is ModPlanEdit.UpdateRange -> {
            val file = edit.place().containingFile ?: return null
            val range = edit.place().range ?: return null
            val virtualFile = file.virtualFile
            if (!isInsideFiles(virtualFile, false, deletedFiles) || isInsideFiles(virtualFile, false, movedFiles)) {
              psiWrites.add(RangeWrite(file, TextRange.create(range), edit.action()))
            }
          }
          is ModPlanEdit.ReplaceText -> {
            val file = edit.place().containingFile ?: return null
            val range = edit.place().range ?: return null
            val virtualFile = file.virtualFile
            if (!isInsideFiles(virtualFile, false, deletedFiles) || isInsideFiles(virtualFile, false, movedFiles)) {
              textWrites.add(TextWrite(file, TextRange.create(range), edit.newText()))
            }
          }
          is ModPlanEdit.Delete -> {
            if (!isInsideFiles(edit.file(), true, deletedFiles) && writtenFiles.add(edit.file())) {
              fileWrites.add(ModDeleteFile(edit.file()))
            }
          }
          is ModPlanEdit.Create -> {
            val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(edit.name())
            fileWrites.add(ModCreateFile(FutureVirtualFile(edit.parent(), edit.name(), fileType), ModCreateFile.Text(edit.text())))
          }
          is ModPlanEdit.CreateDirectory -> {
            fileWrites.add(ModCreateFile(edit.directory(), ModCreateFile.Directory()))
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

      val anchorFile = anchor.containingFile
                       ?: psiWrites.firstNotNullOfOrNull { write -> write.elements.firstNotNullOfOrNull { it.containingFile } }
                       ?: textWrites.firstOrNull()?.file
      val psiCommand = psiCommand(psiWrites, textWrites, anchorFile) ?: return null
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

    /**
     * The text replacements and the [RangeWrite]s follow the PSI writes by range markers in the documents of the copies. The markers exist
     * before the first write. A [RangeWrite] runs among the PSI writes. The text replacements are written after the PSI writes, in the
     * document, from the last place of a file to the first.
     *
     * @return nop if there is nothing to write in the files; null if there is, and no file to build the command for
     */
    private fun psiCommand(writes: List<OrderedWrite>, textWrites: List<TextWrite>, anchor: PsiFile?): ModCommand? {
      if (writes.isEmpty() && textWrites.isEmpty()) return ModCommand.nop()
      if (anchor == null) return null
      return ModCommand.psiUpdate(ActionContext.from(null, anchor)) { updater ->
        // The copy of an injected fragment shares the copy of its host only if the host has one already, so the hosts get theirs first.
        val injectionManager = InjectedLanguageManager.getInstance(anchor.project)
        writes.flatMap { it.elements }.mapNotNull { it.containingFile?.let { file -> injectionManager.getInjectionHost(file)?.containingFile } }
          .distinct().forEach { updater.getWritable(it) }
        val copies = writes.map { write -> write.elements.map { updater.getWritable(it) } }
        val textCopies = textWrites.map { updater.getWritable(it.file) }
        fun createMarker(copy: PsiFile, range: TextRange): RangeMarker {
          val document = copy.viewProvider.document ?: throw IllegalStateException("No document of $copy")
          return document.createRangeMarker(range)
        }
        val rangeMarkers = writes.mapIndexed { index, write ->
          (write as? RangeWrite)?.let { createMarker(copies[index][0] as PsiFile, it.range) }
        }
        val markersByDocument = LinkedHashMap<Document, MutableList<Pair<RangeMarker, String>>>()
        textWrites.forEachIndexed { index, write ->
          val marker = createMarker(textCopies[index], write.range)
          markersByDocument.getOrPut(marker.document) { ArrayList() }.add(marker to write.newText)
        }
        val files = (copies.flatten().mapNotNull { it.containingFile } + textCopies).distinct()
        keepIndentOfFiles(updater, copies.flatten())
        val documentManager = PsiDocumentManager.getInstance(anchor.project)
        writes.forEachIndexed { index, write ->
          // Another edit may already have removed the element, such as a usage that deletes its parent or an edit that replaces the
          // deleted element: there is nothing left to write.
          if (copies[index].all { it.isValid }) {
            when (write) {
              is PsiWrite -> write.write.accept(copies[index])
              is RangeWrite -> {
                val marker = rangeMarkers[index]!!
                documentManager.doPostponedOperationsAndUnblockDocument(marker.document)
                if (marker.isValid) {
                  write.action.accept(copies[index][0] as PsiFile, marker.textRange)
                }
              }
            }
          }
        }
        for ([document, markers] in markersByDocument) {
          documentManager.doPostponedOperationsAndUnblockDocument(document)
          markers.filter { (marker, _) -> marker.isValid }.sortedByDescending { (marker, _) -> marker.startOffset }
            .distinctBy { (marker, _) -> marker.startOffset }
            .forEach { (marker, newText) -> document.replaceString(marker.startOffset, marker.endOffset, newText) }
          documentManager.commitDocument(document)
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
     * Whether [file] is one of [files] or is in one of them; with [strict], only whether it is in one of them. An edit inside a
     * deleted element is not checked here: the write skips it, because its copy is not valid after the element is deleted.
     */
    private fun isInsideFiles(file: VirtualFile?, strict: Boolean, files: List<VirtualFile>): Boolean {
      return file != null && files.any { VfsUtilCore.isAncestor(it, file, strict) }
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
 * @property trashDeletedFiles whether the deleted files go to the trash, see [ModPlan.createPlan]
 */
class ModPlanExecution internal constructor(
  internal val project: Project,
  val commandName: @NlsContexts.Command String,
  internal val refactoringId: @NonNls String,
  internal val writes: List<ModCommand>,
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

  private val snapshots: Map<VirtualFile, FileSnapshot> = snapshotsOf(writes)

  /**
   * The files that the execution keeps as the analysis saw them, see [isUnchanged]: every moved or deleted file, everything inside a
   * moved or deleted directory, and every existing directory that a file is created or moved into.
   */
  internal val snapshotFiles: Set<VirtualFile>
    get() = snapshots.keys

  /**
   * The files that must be writable for the execution: the files that it writes, the files inside a moved or deleted directory, and the
   * directories that a created, moved or deleted file leaves or enters. [ModPlanExecutor] writes nothing if one of them is read-only, so
   * an interactive client asks the user to make them writable before.
   */
  val writtenFilesAndDirectories: List<VirtualFile>
    get() {
      val files = LinkedHashSet(affectedFiles)
      files.addAll(snapshotFiles)
      for (write in writes) {
        when (write) {
          is ModCreateFile -> files.add(existingAncestor(write.file().parent))
          is ModMoveFile -> {
            files.add(existingAncestor(write.targetFile().parent))
            write.file().parent?.let { files.add(it) }
          }
          is ModDeleteFile -> write.file().parent?.let { files.add(it) }
          else -> Unit
        }
      }
      return files.toList()
    }

  /**
   * Whether [file] is as the analysis saw it: at the same path, with the same stamp, and for a directory with the same children. The
   * stamp does not change when a file is renamed or a directory gets a new child. A directory that a file is created or moved into is
   * only at the same path. A file that the execution does not keep is unchanged.
   */
  internal fun isUnchanged(file: VirtualFile): Boolean {
    val snapshot = snapshots[file] ?: return true
    if (!file.isValid || file.path != snapshot.path) return false
    if (snapshot.children != null) return file.children.toSet() == snapshot.children
    return snapshot.stamp == null || stampOf(file) == snapshot.stamp
  }

  /**
   * A file as the analysis saw it.
   *
   * @param withContent whether the stamp and the children are kept too; else only the path
   */
  private class FileSnapshot(file: VirtualFile, withContent: Boolean) {
    val path: String = file.path
    /** The stamp; null if only the path is kept. */
    val stamp: Long? = if (withContent) stampOf(file) else null
    /** The children of a directory; null for a file, and if only the path is kept. */
    val children: Set<VirtualFile>? = if (withContent && file.isDirectory) file.children.toSet() else null
  }

  companion object {
    /** The snapshots of [snapshotFiles]: the moved and deleted files first, in the order of [writes], then the destinations. */
    private fun snapshotsOf(writes: List<ModCommand>): Map<VirtualFile, FileSnapshot> {
      val snapshots = LinkedHashMap<VirtualFile, FileSnapshot>()
      for (write in writes) {
        if (write is ModDeleteFile || write is ModMoveFile) {
          VfsUtilCore.visitChildrenRecursively(writtenFile(write), object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
            override fun visitFile(file: VirtualFile): Boolean = snapshots.putIfAbsent(file, FileSnapshot(file, true)) == null
          })
        }
      }
      for (write in writes) {
        val destination = when (write) {
          is ModCreateFile -> write.file().parent
          is ModMoveFile -> write.targetFile().parent
          else -> continue
        }
        val existingDestination = existingAncestor(destination)
        snapshots.putIfAbsent(existingDestination, FileSnapshot(existingDestination, false))
      }
      return snapshots
    }

    /** The path of [file], also of a file that does not exist yet. */
    internal fun pathOf(file: VirtualFile): String {
      return if (file is FutureVirtualFile) {
        pathOf(file.parent) + "/" + file.name
      }
      else {
        file.path
      }
    }

    /**
     * The stamp of the document of [file], which also moves with the unsaved edits; of the file if it has no document, as a directory.
     * A document that nobody loaded has the stamp of the file, so this does not load one.
     */
    private fun stampOf(file: VirtualFile): Long =
      FileDocumentManager.getInstance().getCachedDocument(file)?.modificationStamp ?: file.modificationStamp

    /** [file], or the first of its parents that exists, if [file] does not exist yet. */
    internal fun existingAncestor(file: VirtualFile): VirtualFile {
      var result = file
      while (result is FutureVirtualFile) {
        result = result.parent
      }
      return result
    }

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
