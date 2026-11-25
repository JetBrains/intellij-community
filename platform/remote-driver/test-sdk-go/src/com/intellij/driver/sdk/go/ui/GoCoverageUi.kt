package com.intellij.driver.sdk.go.ui

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.DriverCallException
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Document
import com.intellij.driver.sdk.MarkupModel
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.TextAttributesKey
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

/**
 * The coverage markers of the editor, indexed by the line number.
 *
 * Only the backend has the coverage renderers. In split mode the markers come from the document of the same file on the backend.
 */
internal fun JEditorUiComponent.coverageLines(): Map<Int, String> = driver.withContext(OnDispatcher.EDT) {
  val target = if (isRemDevMode) RdTarget.BACKEND else RdTarget.DEFAULT
  val document: Document
  val project: Project
  if (isRemDevMode) {
    val path = editor.getVirtualFile().getPath()
    val file = checkNotNull(service(VirtualFileManagerRef::class, target).findFileByUrl("file://$path")) { "The backend has no file '$path'" }
    document = checkNotNull(service(FileDocumentManagerRef::class, target).getDocument(file)) { "The backend has no document of '$path'" }
    project = singleProject(target)
  }
  else {
    document = editor.getDocument()
    project = editor.getProject()
  }
  utility(DocumentMarkupModelRef::class, target).forDocument(document, project, true).getAllHighlighters().mapNotNull { highlighter ->
    val renderer = cast(highlighter, CoverageHighlighterRef::class).getLineMarkerRenderer() ?: return@mapNotNull null
    val key = try {
      renderer.getTextAttributesKey().getExternalName()
    }
    catch (_: DriverCallException) {
      return@mapNotNull null
    }
    if (key !in COVERAGE_KEYS) return@mapNotNull null
    document.getLineNumber(highlighter.getStartOffset()) + 1 to key
  }.toMap()
}

private val COVERAGE_KEYS = setOf("LINE_FULL_COVERAGE", "LINE_PARTIAL_COVERAGE", "LINE_NONE_COVERAGE")

@Remote("com.intellij.openapi.editor.impl.DocumentMarkupModel")
internal interface DocumentMarkupModelRef {
  fun forDocument(document: Document, project: Project, create: Boolean): MarkupModel
}

@Remote("com.intellij.openapi.editor.markup.RangeHighlighter")
internal interface CoverageHighlighterRef {
  fun getLineMarkerRenderer(): CoverageRendererRef?
}

@Remote("com.intellij.coverage.CoverageLineMarkerRenderer", plugin = "com.intellij.platform.coverage/intellij.platform.coverage")
internal interface CoverageRendererRef {
  fun getTextAttributesKey(): TextAttributesKey
}

@Remote("com.intellij.openapi.vfs.VirtualFileManager")
internal interface VirtualFileManagerRef {
  fun findFileByUrl(url: String): VirtualFile?
}

@Remote("com.intellij.openapi.fileEditor.FileDocumentManager")
internal interface FileDocumentManagerRef {
  fun getDocument(file: VirtualFile): Document?
}
