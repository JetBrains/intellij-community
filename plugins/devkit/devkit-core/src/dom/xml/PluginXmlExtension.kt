// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.xml

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import com.intellij.xml.DefaultXmlExtension
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.DescriptorUtil

/**
 * The purpose of this extension is to fix the experience with working with plugin descriptors files.
 *
 * By default, [com.intellij.codeInsight.completion.XmlTagInsertHandler] inserting a tag and its required attributes
 * schedules the basic completion auto popup, which completes all project classes.
 * This is very slow and unusable, as developers expect type-matching classes to be completed.
 *
 * [AttributeValuePresentation.getAutoPopupCompletionType] returned from [getAttributeValuePresentation]
 * requests smart completion, which completes only type-matching classes.
 *
 * @see [PluginXmlCompletionContributor.PluginXmlClassCompletionProvider]
 */
class PluginXmlExtension : DefaultXmlExtension() {

  override fun isAvailable(file: PsiFile): Boolean {
    return DescriptorUtil.isPluginXml(file)
  }

  override fun getAttributeValuePresentation(
    tag: XmlTag?,
    attributeName: String,
    defaultAttributeQuote: String,
  ): AttributeValuePresentation {
    if (tag != null) {
      val domElement = DomManager.getDomManager(tag.project).getDomElement(tag)
      if (domElement is Extension && Extension.isClassNameField(attributeName)) {
        return object : AttributeValuePresentation {
          override fun getPrefix() = defaultAttributeQuote
          override fun getPostfix() = defaultAttributeQuote
          override fun showAutoPopup() = true
          override fun getAutoPopupCompletionType() = CompletionType.SMART
        }
      }
    }
    return super.getAttributeValuePresentation(tag, attributeName, defaultAttributeQuote)
  }

}
