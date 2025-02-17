// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.rename

import com.intellij.compose.ide.plugin.resources.STRINGS_XML_FILENAME
import com.intellij.compose.ide.plugin.resources.VALUES_DIRNAME
import com.intellij.compose.ide.plugin.resources.getAllComposeResourcesDirs
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor

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

  private val XmlAttributeValue.isValueString: Boolean
    get() = containingFile.parent?.name?.startsWith(VALUES_DIRNAME) == true && containingFile.name == STRINGS_XML_FILENAME
}
