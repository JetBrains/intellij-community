// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.ec4j.core.model.Ec4jPath
import org.ec4j.core.model.Glob
import org.editorconfig.Utils
import org.editorconfig.Utils.isEnabled
import org.editorconfig.core.ec4jwrappers.VirtualFileResource
import org.editorconfig.language.messages.EditorConfigBundle.message
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigHeader
import org.jetbrains.annotations.Nls

class EditorConfigPreviewMarkerProvider : LineMarkerProviderDescriptor() {
  override fun getName(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
    return message("line.marker.name.code.preview")
  }

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element is EditorConfigHeader && Handler.isEditorConfigEnabled(element)) {
      val actionGroup = Handler.createActions(element)
      val child = element.getFirstChild()
      if (child != null && child.node.elementType === EditorConfigElementTypes.L_BRACKET) {
        return SectionLineMarkerInfo(actionGroup,
                                     child,
                                     element.getTextRange(),
                                     null)
      }
    }
    return null
  }

  private class SectionLineMarkerInfo(private val myActionGroup: ActionGroup,
                                      element: PsiElement,
                                      range: TextRange,
                                      tooltipProvider: ((PsiElement) -> String)?)
    : LineMarkerInfo<PsiElement>(element, range, AllIcons.General.InspectionsEye, tooltipProvider, null,
                                  GutterIconRenderer.Alignment.LEFT) {
    override fun createGutterRenderer(): GutterIconRenderer =
      object : LineMarkerGutterIconRenderer<PsiElement>(this) {
        override fun getClickAction(): AnAction? = null

        override fun isNavigateAction(): Boolean = true

        override fun getPopupMenuActions(): ActionGroup = myActionGroup
      }
  }

  private class ChooseFileAction(private val myHeader: EditorConfigHeader) : DumbAwareAction(message("editor.preview.open")) {
    override fun actionPerformed(e: AnActionEvent) {
      if (myHeader.isValid) {
        val previewFile = Handler.choosePreviewFile(myHeader.project, Handler.getRootDir(myHeader), Handler.getPattern(myHeader.text))
        if (previewFile != null) {
          val editorConfigFile = myHeader.containingFile.virtualFile
          Handler.openPreview(myHeader.project, editorConfigFile, previewFile)
        }
      }
    }
  }

  private object Handler {
    fun isEditorConfigEnabled(element: PsiElement): Boolean = element.isValid && isEnabled(element.project)

    fun createActions(header: EditorConfigHeader): ActionGroup = DefaultActionGroup(listOf(ChooseFileAction(header)))

    fun getPattern(header: String): String = header.trimStart('[').trimEnd(']')

    fun choosePreviewFile(project: Project, rootDir: VirtualFile, pattern: String): VirtualFile? {
      val descriptor = object : FileChooserDescriptor(true, false, false, false, false, false) {
        override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean =
          (showHiddenFiles || !FileElement.isFileHidden(file))
          && Utils.EDITOR_CONFIG_FILE_NAME != file.name
          && file.length <= EditorConfigEditorProvider.MAX_PREVIEW_LENGTH
          && matchesPattern(rootDir, pattern, file.path)
          || file.isDirectory

        override fun isFileSelectable(file: VirtualFile?): Boolean = file != null && !file.isDirectory
      }.withRoots(rootDir)
      descriptor.isForcedToUseIdeaFileChooser = true
      val fileChooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
      val virtualFiles = fileChooser.choose(project, *VirtualFile.EMPTY_ARRAY)
      return if (virtualFiles.isNotEmpty()) virtualFiles[0] else null
    }

    // TODO verify that the same needle and hay end up being used
    private fun matchesPattern(rootDir: VirtualFile, pattern: String, filePath: String): Boolean {
      val glob = Glob(pattern)
      val resource = VirtualFileResource(rootDir)
      //return EditorConfig.filenameMatches(rootDir.getPath(), pattern, filePath);
      return glob.match(resource.path.relativize(Ec4jPath.Ec4jPaths.of(filePath)))
    }

    fun getRootDir(header: EditorConfigHeader): VirtualFile {
      val psiFile = header.containingFile
      return psiFile.virtualFile.parent
    }

    fun openPreview(project: Project, editorConfigFile: VirtualFile, previewFile: VirtualFile) {
      FileEditorManager.getInstance(project).closeFile(editorConfigFile)
      EditorConfigPreviewManager.getInstance(project).associateWithPreviewFile(editorConfigFile, previewFile)
      FileEditorManager.getInstance(project).openFile(editorConfigFile, true)
    }
  }
}