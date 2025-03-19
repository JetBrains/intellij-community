// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.Condition
import com.intellij.patterns.DomPatterns
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.XmlPatterns.xmlTag
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.Function
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.inspections.DescriptionType
import org.jetbrains.idea.devkit.inspections.INTENTION_ACTION_EP

/**
 * Custom references for select extension attributes/tags.
 */
internal class ExtensionReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registerIntentionAction(registrar)
  }

  private fun registerIntentionAction(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      xmlTag()
        .withLocalName("descriptionDirectoryName")
        .inFile(DomPatterns.inDomFile(IdeaPlugin::class.java))
        .withSuperParent(1, xmlTag()
          .and(DomPatterns.withDom(DomPatterns.domElement(Extension::class.java).with(object : PatternCondition<Extension>("intentionActionEP") {
            override fun accepts(extension: Extension, context: ProcessingContext?): Boolean {
              return extension.extensionPoint?.effectiveQualifiedName == INTENTION_ACTION_EP &&
                     extension.module != null
            }
          })))),
      IntentionActionDescriptionDirectoryNameReferenceProvider())
  }

}

private class IntentionActionDescriptionDirectoryNameReferenceProvider : PsiReferenceProvider() {

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference?> {
    val fileReferenceSet = object : FileReferenceSet(element) {

      override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem?>? {
        return DIRECTORY_FILTER
      }
    }
    fileReferenceSet.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION,
                                      Function { getIntentionDescriptionRootDirectory(element) })

    return fileReferenceSet.allReferences
  }

  private fun getIntentionDescriptionRootDirectory(element: PsiElement): Collection<PsiFileSystemItem> {
    val module = ModuleUtil.findModuleForPsiElement(element)?: return emptyList()
    return DescriptionType.INTENTION.getDescriptionFolderDirs(module).map { it ->
      element.manager.findDirectory(it.virtualFile)!!
    }
  }

}
