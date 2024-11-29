// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.DiffContext
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.MessageDiffRequest
import com.intellij.diff.tools.combined.CombinedBlockProducer
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.diff.tools.combined.CombinedDiffManager
import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.ListSelection
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.impl.patch.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.JComponentFileEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.actions.diff.prepareCombinedBlocksFromProducers
import com.intellij.openapi.vcs.changes.patch.PatchFileType
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.MutableDiffRequestChainProcessor
import com.intellij.openapi.diff.impl.DiffTitleWithDetailsCustomizers.getTitleCustomizers
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.util.ui.update.queueTracked
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import javax.swing.event.HyperlinkEvent

internal class DiffPatchFileEditorProvider : FileEditorProvider, StructureViewFileEditorProvider, DumbAware {
  /**
   * See [com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.accept]
   */
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!Registry.`is`("enable.patch.file.diff.viewer")) return false
    return PatchFileType.isPatchFile(file) &&
           TextEditorProvider.isTextFile(file) &&
           !SingleRootFileViewProvider.isTooLargeForContentLoading(file)
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document == null) {
      val label = DiffUtil.createMessagePanel(VcsBundle.message("patch.parse.no.document.error"))
      return JComponentFileEditor(file, label, DiffBundle.message("diff.file.editor.name"))
    }

    if (CombinedDiffRegistry.isEnabled()) {
      val processor = CombinedDiffManager.getInstance(project).createProcessor()
      processor.context.putUserData(DiffUserDataKeysEx.PATCH_FILE_PREVIEW_MODIFICATION_SWITCH,
                                    Runnable { switchToEditableView(project, file) })
      processor.setBlocks(buildCombinedDiffModel(document))

      val editor = DiffEditorViewerFileEditor(file, processor)

      val updateQueue = MergingUpdateQueue("DiffPatchFileEditorProvider", 300, true, editor.component, editor)
      document.addDocumentListener(CombinedViewerPatchChangeListener(processor, updateQueue), editor)

      return editor
    }
    else {
      val processor = MutableDiffRequestChainProcessor(project, null)
      processor.context.putUserData(DiffUserDataKeysEx.PATCH_FILE_PREVIEW_MODIFICATION_SWITCH,
                                    Runnable { switchToEditableView(project, file) })
      processor.chain = PatchDiffRequestChain(document)

      val editor = DiffEditorViewerFileEditor(file, processor)

      val updateQueue = MergingUpdateQueue("DiffPatchFileEditorProvider", 300, true, editor.component, editor)
      document.addDocumentListener(RequestProcessorPatchChangeListener(processor, updateQueue), editor)

      return editor
    }
  }

  override fun getEditorTypeId(): String {
    return "DiffPatchFileEditorProvider"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
  }

  override fun getStructureViewBuilder(project: Project, file: VirtualFile): StructureViewBuilder? {
    return null
  }
}

private fun switchToEditableView(project: Project, file: VirtualFile) {
  FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
}

private fun buildCombinedDiffModel(document: Document): List<CombinedBlockProducer> {
  val producers = createDiffRequestProducers(document)
  val diffModel = prepareCombinedBlocksFromProducers(producers)
  return diffModel
}

private fun createDiffRequestProducers(document: Document): List<ChangeDiffRequestChain.Producer> {
  try {
    val patchText = document.text
    if (patchText.isBlank()) {
      // avoid patch parse error on empty files
      val message = VcsBundle.message("patch.parse.error.empty.file")
      return listOf(createStatusMessageContent(document, message))
    }

    val reader = PatchReader(patchText)
    reader.parseAllPatches()
    return reader.allPatches.map { PatchDiffRequestProducer(it) }
  }
  catch (e: PatchSyntaxException) {
    val message = VcsBundle.message("patch.parse.error", e.message)
    return listOf(createStatusMessageContent(document, message))
  }
}

private fun createStatusMessageContent(document: Document, message: @Nls String): ChangeDiffRequestChain.Producer {
  val file = FileDocumentManager.getInstance().getFile(document)!!
  return StatusDiffRequestProducer(VcsUtil.getFilePath(file), message)
}

private class CombinedViewerPatchChangeListener(
  val processor: CombinedDiffComponentProcessor,
  val queue: MergingUpdateQueue,
) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    queue.queueTracked(Update.create(this) {
      processor.setBlocks(buildCombinedDiffModel(event.document))
    })
  }
}

private class RequestProcessorPatchChangeListener(
  val processor: MutableDiffRequestChainProcessor,
  val queue: MergingUpdateQueue,
) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    queue.queueTracked(Update.create(this) {
      processor.chain = PatchDiffRequestChain(event.document)
    })
  }
}

private class PatchDiffRequestChain(val document: Document) : ChangeDiffRequestChain.Async() {
  override fun loadRequestProducers(): ListSelection<out ChangeDiffRequestChain.Producer> {
    return ListSelection.createAt(createDiffRequestProducers(document), 0)
  }
}

private class PatchDiffRequestProducer(private val patch: FilePatch) : ChangeDiffRequestChain.Producer {
  override fun getName(): @Nls String = patch.afterName ?: patch.beforeName ?: "patch"
  override fun getFilePath(): FilePath = VcsUtil.getFilePath(name, false)

  override fun getFileStatus(): FileStatus {
    if (patch.beforeName == null && patch.afterName == null) return FileStatus.MERGED_WITH_CONFLICTS
    if (patch.beforeName == null) return FileStatus.ADDED
    if (patch.afterName == null) return FileStatus.DELETED
    return FileStatus.MODIFIED
  }

  @Throws(ProcessCanceledException::class)
  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    if (patch is TextFilePatch) {
      val patchDiffRequest = PatchDiffRequest(patch, null, patch.beforeName, patch.afterName)
      return DiffUtil.addTitleCustomizers(
        patchDiffRequest, getTitleCustomizers(patchDiffRequest.patch.beforeName, patchDiffRequest.patch.afterName)
      )
    }
    if (patch is BinaryFilePatch) {
      return MessageDiffRequest(VcsBundle.message("patch.is.binary.text"))
    }
    throw IllegalStateException("Unknown patch type: $patch")
  }
}

private class StatusDiffRequestProducer(
  private val filePath: FilePath,
  private val message: @Nls String,
) : ChangeDiffRequestChain.Producer {
  override fun getName(): @Nls String = filePath.name
  override fun getFilePath(): FilePath = filePath
  override fun getFileStatus(): FileStatus = FileStatus.MERGED_WITH_CONFLICTS

  @Throws(ProcessCanceledException::class)
  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    return MessageDiffRequest(message)
  }
}

internal fun listenTypingAttempts(diffContext: DiffContext, editor: Editor) {
  val onTypingSwitch = diffContext.getUserData(DiffUserDataKeysEx.PATCH_FILE_PREVIEW_MODIFICATION_SWITCH)
  if (onTypingSwitch != null) {
    EditorModificationUtil.setReadOnlyHint(editor, DiffBundle.message("patch.editing.viewer.hint.enable.editing.text"), { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        onTypingSwitch.run()
      }
    })
  }
}
