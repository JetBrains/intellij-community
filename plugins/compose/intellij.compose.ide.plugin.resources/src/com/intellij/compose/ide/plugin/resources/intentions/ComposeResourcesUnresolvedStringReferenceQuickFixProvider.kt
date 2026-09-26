// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.STRINGS_XML_FILENAME
import com.intellij.compose.ide.plugin.resources.getComposeResourcesDir
import com.intellij.compose.ide.plugin.resources.intentions.quickfix.CreateStringResourceQuickFix
import com.intellij.compose.ide.plugin.resources.isComposeResClass
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class ComposeResourcesUnresolvedStringReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<KtSimpleNameReference>() {
  override fun getReferenceClass(): Class<KtSimpleNameReference> = KtSimpleNameReference::class.java

  override fun registerFixes(ref: KtSimpleNameReference, registrar: QuickFixActionRegistrar) {
    val quickFix = createQuickFixIfApplicable(ref) ?: return
    registrar.register(quickFix)
  }
}

private fun createQuickFixIfApplicable(ref: KtSimpleNameReference): CreateStringResourceQuickFix? {
  val element = ref.expression as? KtNameReferenceExpression ?: return null

  val resDotAccessorDotName = element.parent as? KtDotQualifiedExpression ?: return null
  if (resDotAccessorDotName.selectorExpression != element) return null

  val module = element.module ?: return null
  val resDotAccessor = resDotAccessorDotName.receiverExpression as? KtDotQualifiedExpression ?: return null
  if (!resDotAccessor.isComposeResClass) return null

  val accessorPart = resDotAccessor.selectorExpression as? KtNameReferenceExpression ?: return null


  val accessor = accessorPart.getReferencedName()
  if (ResourceType.stringEntries.none { accessor == it.accessorName }) return null

  val resourceType = ResourceType.fromAccessor(accessor)
  val resourceName = element.getReferencedName()
  if (resourceName.isEmpty()) return null

  val composeResourcesDirVirtualFile = module.getComposeResourcesDir() ?: return null
  val valuesDir = composeResourcesDirVirtualFile.findChild(ResourceType.STRING.dirName)
  val stringsVirtualFile = valuesDir?.findChild(STRINGS_XML_FILENAME)

  val rootTag = stringsVirtualFile
    ?.let { element.manager.findFile(it) as? XmlFile }
    ?.rootTag

  if (rootTag?.hasSubTagWithName(resourceType.typeName, resourceName) == true) return null

  return CreateStringResourceQuickFix(
    resourceName = resourceName,
    resourceType = resourceType,
    sourceKtFileUrl = element.containingFile.virtualFile.url,
    stringsXmlUrl = stringsVirtualFile?.url,
    composeResourcesDirUrl = composeResourcesDirVirtualFile.url,
  )
}

internal fun XmlTag.hasSubTagWithName(tagName: String, name: String): Boolean {
  return findSubTags(tagName).any { it.getAttributeValue("name") == name }
}