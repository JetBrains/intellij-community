// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.compose.ide.plugin.resources.ComposeResourcesManager
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.STRINGS_XML_FILENAME
import com.intellij.compose.ide.plugin.resources.findComposeResourcesDirFor
import com.intellij.compose.ide.plugin.resources.intentions.hasSubTagWithName
import com.intellij.compose.ide.plugin.resources.psi.getResourcePackageName
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

private const val NEW_STRING_RESOURCE_INNER_TEXT_PLACEHOLDER = "TODO"

private data class TemplateData(val template: Template, val offset: Int)

internal class CreateStringResourceQuickFix(
  private val resourceName: String,
  private val resourceType: ResourceType,
  private val sourceKtFileUrl: String,
  private val stringsXmlUrl: String?,
  private val composeResourcesDirUrl: String,
) : IntentionAction, PriorityAction {

  override fun getText(): String =
    ComposeIdeBundle.message("compose.resources.intention.create.string.resource.text", resourceType.accessorName, resourceName)

  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.resources.intention.create.extension.property.family.name")

  override fun getPriority(): Priority = Priority.TOP

  override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val vfm = VirtualFileManager.getInstance()
    return vfm.findFileByUrl(sourceKtFileUrl) != null
           && resourceType.isStringType
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val vfm = VirtualFileManager.getInstance()
    val psiManager = PsiManager.getInstance(project)

    val virtualStringsXmlFile = findOrCreateStringsXmlFile(project, composeResourcesDirUrl, stringsXmlUrl) ?: return
    val stringsXmlFile = psiManager.findFile(virtualStringsXmlFile) as? XmlFile ?: return
    val rootTag = stringsXmlFile.rootTag ?: return
    if (rootTag.hasSubTagWithName(resourceType.typeName, resourceName)) return

    val virtualSourceKtFile = vfm.findFileByUrl(sourceKtFileUrl) ?: return
    val ktFile = psiManager.findFile(virtualSourceKtFile) as? KtFile ?: return

    val path = stringsXmlFile.virtualFile.toNioPathOrNull() ?: return
    val composeResourcesDir = project.findComposeResourcesDirFor(path) ?: return
    val composeResources = project.service<ComposeResourcesManager>().composeResourcesByModulePath[composeResourcesDir.moduleName] ?: return
    val resourcePackageName = composeResourcesDir.getResourcePackageName(project, composeResources.packageOfResClass)

    val addedTag = WriteCommandAction.writeCommandAction(project, stringsXmlFile, ktFile)
                     .withName(text)
                     .compute<XmlTag?, RuntimeException> {
                       ktFile.addResourceImport(resourcePackageName, resourceName)
                       rootTag.addEntry(resourceType, resourceName)
                     } ?: return

    val documentManager = PsiDocumentManager.getInstance(project)
    stringsXmlFile.viewProvider.document?.let(documentManager::commitDocument)
    ktFile.viewProvider.document?.let(documentManager::commitDocument)

    val templateData = WriteCommandAction.writeCommandAction(project)
                         .compute<TemplateData?, RuntimeException> {
                           buildTemplateFromTag(addedTag)
                         } ?: return

    runInEdt {
      project.startTemplate(virtualStringsXmlFile, templateData)
    }
  }
}

private fun buildTemplateFromTag(tag: XmlTag): TemplateData? {
  val todoElement = tag.findTodoPlaceholder() ?: return null
  val templateBuilder = TemplateBuilderImpl(todoElement)
  templateBuilder.replaceElement(todoElement, NEW_STRING_RESOURCE_INNER_TEXT_PLACEHOLDER)
  val template = templateBuilder.buildInlineTemplate()
  return TemplateData(template, todoElement.textRange.startOffset)
}

private fun XmlTag.findTodoPlaceholder(): PsiElement? {
  val targetTag = findFirstSubTag("item") ?: this
  return targetTag.value.textElements.firstOrNull { it.text == NEW_STRING_RESOURCE_INNER_TEXT_PLACEHOLDER }
}

private fun Project.startTemplate(xmlVirtualFile: VirtualFile, data: TemplateData) {
  val fileEditorManager = FileEditorManager.getInstance(this)
  val descriptor = OpenFileDescriptor(this, xmlVirtualFile, data.offset)
  val openedEditor = fileEditorManager.openTextEditor(descriptor, true) ?: return

  openedEditor.caretModel.moveToOffset(data.offset)

  TemplateManager.getInstance(this).startTemplate(openedEditor, data.template, object : TemplateEditingAdapter() {
    override fun templateFinished(template: Template, brokenOff: Boolean) {
      runInEdt { fileEditorManager.closeFile(xmlVirtualFile) }
    }

    override fun templateCancelled(template: Template?) {
      runInEdt { fileEditorManager.closeFile(xmlVirtualFile) }
    }
  })
}

private fun findOrCreateStringsXmlFile(
  project: Project,
  composeResourcesDirUrl: String,
  stringsXmlUrl: String?,
): VirtualFile? {
  val vfm = VirtualFileManager.getInstance()

  stringsXmlUrl?.let { url ->
    vfm.findFileByUrl(url)?.let { return it }
  }

  val composeResourcesDir = vfm.findFileByUrl(composeResourcesDirUrl) ?: return null

  return WriteCommandAction.writeCommandAction(project)
    .withName(ComposeIdeBundle.message("compose.resources.intention.create.extension.property.family.name"))
    .compute<VirtualFile?, RuntimeException> {
      val valuesDir = composeResourcesDir.findChild(ResourceType.STRING.dirName)
                      ?: composeResourcesDir.createChildDirectory(project, ResourceType.STRING.dirName)

      valuesDir.findChild(STRINGS_XML_FILENAME)
      ?: valuesDir.createChildData(project, STRINGS_XML_FILENAME).also {
        VfsUtil.saveText(it, "<resources>\n</resources>\n")
      }
    }
}

private fun XmlTag.addEntry(resourceType: ResourceType, name: String): XmlTag {
  val isCompoundType = resourceType == ResourceType.STRING_ARRAY || resourceType == ResourceType.PLURAL_STRING
  val bodyText = if (isCompoundType) "" else NEW_STRING_RESOURCE_INNER_TEXT_PLACEHOLDER

  val tag = createChildTag(resourceType.typeName, null, bodyText, false).apply {
    setAttribute("name", name)
  }

  if (isCompoundType) {
    val item = createChildTag("item", null, NEW_STRING_RESOURCE_INNER_TEXT_PLACEHOLDER, false).apply {
      if (resourceType == ResourceType.PLURAL_STRING) {
        setAttribute("quantity", "other")
      }
    }
    tag.addSubTag(item, false)
  }

  return addSubTag(tag, false)
}

private fun KtFile.addResourceImport(packageName: String, name: String) {
  val importPath = ImportPath.fromString("$packageName.$name")
  val importList = importList ?: return
  if (importList.imports.any { it.importPath == importPath }) return
  val importDirective = KtPsiFactory(project).createImportDirective(importPath)
  importList.add(importDirective)
}