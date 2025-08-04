// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.psi

import com.intellij.compose.ide.plugin.resources.ComposeResourcesGenerationService
import com.intellij.compose.ide.plugin.resources.findComposeResourcesDirFor
import com.intellij.compose.ide.plugin.resources.isValidInnerComposeResourcesDirName
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.*
import com.intellij.psi.xml.*
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Heavily inspired by [com.android.tools.idea.res.ResourceNotificationManager]
 */
private val PsiTreeChangeEvent.isIgnorable: Boolean
  get() {
    if (file?.language == KotlinLanguage.INSTANCE) return true
    val parentName = getParentName() ?: return true
    if (!parentName.isValidInnerComposeResourcesDirName) return true

    // We can ignore edits in whitespace, XML error nodes, and modification in comments.
    // (Note that editing text in an attribute value, including whitespace characters,
    // is not a PsiWhiteSpace element; it's an XmlToken of token type XML_ATTRIBUTE_VALUE_TOKEN
    // Moreover, We only ignore the modification of commented texts (in such case the type of
    // parent is XmlComment), because the user may *mark* some components/attributes as comments
    // for debugging purpose. In that case the child is instance of XmlComment but parent isn't,
    // so we will NOT ignore the event.
    if (child is PsiErrorElement || parent is XmlComment) return true

    // Editing text or whitespace has no effect outside values files.
    if (child is PsiWhiteSpace || child is XmlText || parent is XmlText) return true

    val file = this.file ?: (this.child as? PsiFile)
    // Spurious events from the IDE doing internal things, such as the formatter using a light
    // virtual filesystem to process text formatting chunks etc.
    return file != null && (file.parent == null || !file.viewProvider.isPhysical)
  }

class ComposeResourcesPsiChangesListener(private val project: Project) : PsiTreeChangeAdapter() {
  private var ignoreChildrenChanged = false

  /** propagating file rename events */
  override fun propertyChanged(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true
    if (event.propertyName != "fileName" || event.isIgnorable) return
    notice(event)
  }

  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = false
  }

  override fun childAdded(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true
    if (event.isIgnorable) return

    val child = event.child ?: return
    val parent = event.parent ?: return

    if (child is XmlAttribute && parent is XmlTag) {
      // Typing in a new attribute. Don't need to do any rendering until there is an actual value.
      if (child.valueElement == null) return
    }
    else if (parent is XmlAttribute && child is XmlAttributeValue) {
      if (child.value.isEmpty()) return // Just added a new blank attribute; nothing to render yet.
    }

    notice(event)
  }

  // todo allow to trigger generation in case misplaced files
  override fun childRemoved(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true
    if (event.isIgnorable && event.parent?.namedUnwrappedElement?.name != "composeResources") return

    val child = event.child ?: return
    val parent = event.parent ?: return
    if (parent is XmlAttribute && child is XmlToken) {
      // Typing in attribute name. Don't need to do any rendering until there is an actual
      // value.
      val valueElement = parent.valueElement
      if (valueElement == null || valueElement.value.isEmpty()) {
        return
      }
    }

    notice(event)
  }

  override fun childReplaced(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true
    if (event.isIgnorable) return

    // skip changes in text values, they don't influence accessors
    if (event.parent is XmlText) return

    val child = event.child
    if (child is PsiWhiteSpace) return
    val parent = event.parent
    if (parent is XmlAttribute && child is XmlToken) {
      // Typing in attribute name. Don't need to do any rendering until there is an actual value.
      val valueElement = parent.valueElement
      if (valueElement == null || valueElement.value.isEmpty()) return
    }
    else if (parent is XmlAttributeValue && child is XmlToken && event.oldChild != null) {
      val newText = child.getText()
      val prevText = event.oldChild.text
      if (newText.isEmpty() && prevText.isEmpty()) return
    }

    notice(event)
  }

  override fun childMoved(event: PsiTreeChangeEvent) {
    if (event.isIgnorable) return
    notice(event)
  }

  override fun childrenChanged(event: PsiTreeChangeEvent) {
    if (ignoreChildrenChanged) return
    notice(event)
  }

  private fun notice(event: PsiTreeChangeEvent) {
    if (project.isDisposed) return
    if (event.isIgnorable) return
    val virtualFile = event.getVirtualFile() ?: return
    val composeResourcesGenerationService = project.service<ComposeResourcesGenerationService>()
    val path = virtualFile.toNioPathOrNull() ?: return
    val composeResourcesDir = project.findComposeResourcesDirFor(path) ?: return
    composeResourcesGenerationService.tryEmit(composeResourcesDir)
  }

  private fun PsiTreeChangeEvent.getVirtualFile(): VirtualFile? = file?.virtualFile /* for string values */
                                                                  ?: (this.parent as? PsiDirectory)?.virtualFile /* for files */
                                                                  ?: (this.child as? PsiFile)?.virtualFile /* CHILD_MOVED case */

}

private fun PsiTreeChangeEvent.getParentName(): String? = file?.parent?.name /* for string values */
                                                          ?: parent?.namedUnwrappedElement?.name /* for files */
                                                          ?: newParent?.namedUnwrappedElement?.name /* CHILD_MOVED case */
