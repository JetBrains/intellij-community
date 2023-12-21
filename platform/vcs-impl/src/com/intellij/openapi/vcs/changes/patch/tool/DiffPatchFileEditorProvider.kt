// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.MessageDiffRequest
import com.intellij.diff.tools.combined.*
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.ListSelection
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.impl.patch.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel.Companion.prepareCombinedDiffModelRequestsFromProducers
import com.intellij.openapi.vcs.changes.patch.PatchFileType
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.MutableDiffRequestChainProcessor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import java.util.*

internal class DiffPatchFileEditorProvider : FileEditorProvider, StructureViewFileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!Registry.`is`("enable.patch.file.diff.viewer")) return false
    return PatchFileType.isPatchFile(file) && FileDocumentManager.getInstance().getDocument(file) != null
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val document = FileDocumentManager.getInstance().getDocument(file)!!

    if (CombinedDiffRegistry.isEnabled()) {
      val model = CombinedDiffModelImpl(project, null)
      model.setBlocks(buildCombinedDiffModel(document))

      val factory = project.service<CombinedDiffComponentFactoryProvider>().create(model)
      val editor = CombinedDiffComponentEditor(file, factory)
      document.addDocumentListener(CombinedViewerPatchChangeListener(model), editor)

      return editor
    }
    else {
      val chain = PatchDiffRequestChain(document)
      val processor = MutableDiffRequestChainProcessor(project, chain)

      val editor = DiffRequestProcessorEditor(file, processor)
      document.addDocumentListener(RequestProcessorPatchChangeListener(processor), editor)

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

private fun buildCombinedDiffModel(document: Document): Map<CombinedBlockId, DiffRequestProducer> {
  val producers = createDiffRequestProducers(document)
  val diffModel = prepareCombinedDiffModelRequestsFromProducers(producers)
  return diffModel
}

private fun createDiffRequestProducers(document: Document): List<ChangeDiffRequestChain.Producer> {
  try {
    val reader = PatchReader(document.text)
    reader.parseAllPatches()
    return reader.allPatches.map { PatchDiffRequestProducer(it) }
  }
  catch (e: PatchSyntaxException) {
    val file = FileDocumentManager.getInstance().getFile(document)!!
    val message = VcsBundle.message("patch.parse.error", e.message)
    return listOf(ErrorDiffRequestProducer(file, message))
  }
}

private class CombinedViewerPatchChangeListener(val model: CombinedDiffModelImpl) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    model.setBlocks(buildCombinedDiffModel(event.document))
  }
}

private class RequestProcessorPatchChangeListener(val processor: MutableDiffRequestChainProcessor) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    processor.chain = PatchDiffRequestChain(event.document)
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
      return PatchDiffRequest(patch, null, patch.beforeName, patch.afterName)
    }
    if (patch is BinaryFilePatch) {
      return MessageDiffRequest(VcsBundle.message("patch.is.binary.text"))
    }
    throw IllegalStateException("Unknown patch type: $patch")
  }
}

private class ErrorDiffRequestProducer(private val file: VirtualFile,
                                       private val message: @Nls String) : ChangeDiffRequestChain.Producer {
  override fun getName(): @Nls String = file.name
  override fun getFilePath(): FilePath = VcsUtil.getFilePath(file)
  override fun getFileStatus(): FileStatus = FileStatus.MERGED_WITH_CONFLICTS

  @Throws(ProcessCanceledException::class)
  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    return ErrorDiffRequest(message)
  }
}
