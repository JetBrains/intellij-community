// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.dom.ContentModuleVisibility
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class ContentModuleNamespaceInspection : DevKitPluginXmlInspectionBase() {

  private val namespaceLengthRange = 5..30
  private val namespaceRegex = Regex("[a-zA-Z0-9]+([_-][a-zA-Z0-9]+)*")

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    val content = element as? ContentDescriptor ?: return
    val namespace = content.namespace
    checkNamespace(content, namespace, holder)
    checkContentElementsHaveTheSameNamespace(content, namespace, holder)
  }

  private fun checkNamespace(
    content: ContentDescriptor,
    namespaceDomValue: GenericAttributeValue<String>,
    holder: DomElementAnnotationHolder,
  ) {
    val namespace = namespaceDomValue.value
    when {
      namespace == null -> {
        if (anyContentModuleIsNonPrivate(content)) {
          holder.createProblem(
            content,
            message("inspection.content.module.namespace.missing"),
            AddNamespaceFix(isIntellijProject = IntelliJProjectUtil.isIntelliJPlatformProject(content.xmlElement!!.project))
          )
        }
      }
      namespace.length !in namespaceLengthRange -> {
        holder.createProblem(namespaceDomValue, message("inspection.content.module.namespace.invalid.length"))
      }
      !namespaceRegex.matches(namespace) -> {
        holder.createProblem(namespaceDomValue, message("inspection.content.module.namespace.invalid", namespaceRegex.pattern))
      }
    }
  }

  private fun anyContentModuleIsNonPrivate(content: ContentDescriptor): Boolean {
    return content.moduleEntry.any {
      val visibility = it.name.value?.contentModuleVisibility?.value ?: ContentModuleVisibility.PRIVATE
      visibility != ContentModuleVisibility.PRIVATE
    }
  }

  private fun checkContentElementsHaveTheSameNamespace(
    content: ContentDescriptor,
    namespaceDomValue: GenericAttributeValue<String>,
    holder: DomElementAnnotationHolder,
  ) {
    val xmlFile = content.xmlElement?.containingFile as? XmlFile ?: return
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile) ?: return
    val firstContent = ideaPlugin.content.first().takeIf { it != content } ?: return
    val firstNamespaceValue = firstContent.namespace.value
    if (namespaceDomValue.value != firstNamespaceValue) {
      holder.createProblem(namespaceDomValue, message("inspection.content.module.namespace.mismatch", firstNamespaceValue))
    }
  }

  private class AddNamespaceFix(private val isIntellijProject: Boolean) : LocalQuickFix {

    private val defaultMonorepoNamespace = "jetbrains"

    override fun getFamilyName(): String {
      return message("inspection.content.module.namespace.missing.fix.add.family.name")
    }

    override fun getName(): @IntentionName String {
      return if (isIntellijProject) message("inspection.content.module.namespace.missing.fix.add.name", defaultMonorepoNamespace)
      else super.name
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val content = DomUtil.getDomElement(descriptor.psiElement) as? ContentDescriptor ?: return
      val namespace = content.namespace
      if (isIntellijProject) {
        namespace.stringValue = defaultMonorepoNamespace
      }
      else {
        namespace.stringValue = ""
        val editor = PsiEditorUtil.findEditor(descriptor.psiElement) ?: return
        editor.caretModel.moveToOffset(namespace.xmlElement!!.endOffset - 1)
      }
    }
  }
}
