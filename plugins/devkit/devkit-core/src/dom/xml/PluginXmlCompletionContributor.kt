// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.xml

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.XmlAttributeInsertHandler
import com.intellij.codeInsight.completion.XmlAttributeReferenceCompletionProvider
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.XmlPatterns.xmlAttribute
import com.intellij.psi.impl.source.xml.XmlAttributeReference
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.DescriptorUtil

/**
 * The purpose of this completion contributor is to fix the experience with working with plugin descriptors files.
 *
 * By default, XMLAttributeInsertHandler schedules the basic completion auto popup, which completes all project classes.
 * This is very slow and unusable, as developers expect type-matching classes to be completed.
 *
 * [PluginXmlClassCompletionProvider] overrides the default insert behavior by cancelling the basic completion auto popup
 * and invoking smart completion, which completes only type-matching classes.
 *
 * @see [PluginXmlExtension]
 */
class PluginXmlCompletionContributor : CompletionContributor() {

  init {
    extend(CompletionType.BASIC, psiElement().inside(xmlAttribute()), PluginXmlClassCompletionProvider())
  }

  private class PluginXmlClassCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
      parameters: CompletionParameters,
      context: ProcessingContext,
      result: CompletionResultSet,
    ) {
      if (!DescriptorUtil.isPluginXml(parameters.originalFile)) return
      val position = parameters.position
      val containingTag = position.parentOfType<XmlTag>()
      val project = position.project
      if (DomManager.getDomManager(project).getDomElement(containingTag) !is Extension) return
      val reference = position.containingFile.findReferenceAt(parameters.offset)
      if (reference is XmlAttributeReference) {
        XmlAttributeReferenceCompletionProvider.addAttributeReferenceCompletionVariants(reference, result) { context, item ->
          XmlAttributeInsertHandler.INSTANCE.handleInsert(context, item)
          val attributeName = item.lookupString
          if (Extension.isClassNameField(attributeName)) {
            // cancel XmlAttributeInsertHandler's basic auto popup schedule and invoke smart:
            AutoPopupController.getInstance(project).scheduleAutoPopup(context.editor, CompletionType.SMART, null)
          }
        }
        result.stopHere()
      }
    }
  }

}
