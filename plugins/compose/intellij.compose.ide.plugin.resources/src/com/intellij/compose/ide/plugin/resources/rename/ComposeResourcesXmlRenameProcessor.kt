// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.rename

import com.intellij.compose.ide.plugin.resources.*
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor
import com.intellij.ui.EditorTextField
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.reflect.KProperty

/**
 * A [RenameXmlAttributeProcessor] based processor for renaming XML attributes in Compose resource files.
 *
 * It must run before [ResourceReferenceRenameProcessor] because the processor also claims it can process it.
 */
internal class ComposeResourcesXmlRenameProcessor : RenameXmlAttributeProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean {
    if (element !is XmlAttributeValue || !element.isValueString) return false
    val resourceDirectoryPath = element.containingFile.parent?.parent?.virtualFile ?: return false
    return element.project.getAllComposeResourcesDirs().any { it.directoryPath == resourceDirectoryPath.toNioPathOrNull() }
  }

  override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo?>, listener: RefactoringElementListener?) {
    // filter out Android R.string values with matching name
    val nonAndroidStringUsages = usages.filterNot { it?.reference?.javaClass?.name == ANDROID_RESOURCE_REFERENCE }.toTypedArray()
    super.renameElement(element, newName, nonAndroidStringUsages, listener)
  }

  private val XmlAttributeValue.isValueString: Boolean
    get() = containingFile.parent?.name?.startsWith(VALUES_DIRNAME) == true && containingFile.name == STRINGS_XML_FILENAME
}

/** Custom [RenameHandler] for Compose resources string values rename in XML files. */
internal class ComposeResourcesXmlRenameHandler : RenameHandler, TitledHandler, ComposeResourcesXmlBase {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
    return isComposeResourcesElement(file)
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
    if (file == null) return
    val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: run {
      val offset = CommonDataKeys.CARET.getData(dataContext)?.offset ?: return
      val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
      file.findElementAt(offset)
    } ?: return

    val property = getKotlinPropertyFromComposeResource(element) ?: return
    val newName = NEW_NAME_COMPOSE_RESOURCE.getData(dataContext)
    ResourceRenameDialog(project, property, null, editor, newName).show(dataContext)
    ComposeResourcesUsageCollector.logAction(fusActionType, fusResourceBaseType, null)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    val file = CommonDataKeys.PSI_FILE.getData(dataContext)
    invoke(project, editor, file, dataContext)
  }

  override fun getActionTitle(): @NlsActions.ActionText String? = ComposeIdeBundle.message("compose.resources.rename.string.values")

  override val fusActionType: ComposeResourcesUsageCollector.ActionType
    get() = ComposeResourcesUsageCollector.ActionType.RENAME
}

private class ResourceRenameDialog(
  project: Project,
  resourceReferenceElement: KtProperty,
  nameSuggestionContext: PsiElement?,
  editor: Editor?,
  providedName: String?,
) : RenameDialog(project, resourceReferenceElement, nameSuggestionContext, editor) {

  init {
    if (providedName != null) {
      (nameSuggestionsField.focusableComponent as? EditorTextField)?.text = providedName
    }
  }

  fun show(dataContext: DataContext) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      val newTestingName = NEW_NAME_COMPOSE_RESOURCE.getData(dataContext) ?: PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext)
                           ?: return
      performRename(newTestingName)
      close(OK_EXIT_CODE)
    }
    else {
      super.show()
    }
  }
}

private val NEW_NAME_COMPOSE_RESOURCE: DataKey<String> = DataKey.create(::NEW_NAME_COMPOSE_RESOURCE.qualifiedName<ComposeResourcesXmlRenameHandler>())

private inline fun <reified T> KProperty<*>.qualifiedName(): String = T::class.java.name + "." + this.name
