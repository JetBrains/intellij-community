// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.ExtensionCandidate
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass

@NonNls
internal const val DESCRIPTION_HTML = "description.html"

/**
 * @see DescriptionTypeResolver
 */
internal enum class DescriptionType(
  private val myClassName: String,
  private val myFallbackClassName: String?,
  private val descriptionFolder: String,
  private val myHasBeforeAfterTemplateFiles: Boolean,
) {
  INTENTION(CommonIntentionAction::class.java.getName(), IntentionAction::class.java.getName(), "intentionDescriptions", true) {
    override fun createDescriptionTypeResolver(module: Module, psiClass: PsiClass): DescriptionTypeResolver {
      return IntentionDescriptionTypeResolver(module, psiClass)
    }
  },

  INSPECTION(InspectionProfileEntry::class.java.getName(), null, "inspectionDescriptions", false) {
    override fun createDescriptionTypeResolver(module: Module, psiClass: PsiClass): DescriptionTypeResolver {
      return InspectionDescriptionTypeResolver(module, psiClass)
    }
  },

  POSTFIX_TEMPLATES(PostfixTemplate::class.java.getName(), null, "postfixTemplates", true) {
    override fun createDescriptionTypeResolver(module: Module, psiClass: PsiClass): DescriptionTypeResolver {
      return PostfixTemplateDescriptionTypeResolver(module, psiClass)
    }
  };

  abstract fun createDescriptionTypeResolver(module: Module, psiClass: PsiClass): DescriptionTypeResolver

  fun matches(psiClass: PsiClass): Boolean {
    val baseClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(myClassName, psiClass.getResolveScope())
    if (baseClass != null) {
      return psiClass.isInheritor(baseClass, true)
    }

    return myFallbackClassName != null && InheritanceUtil.isInheritor(psiClass, myFallbackClassName)
  }

  fun hasBeforeAfterTemplateFiles(): Boolean = myHasBeforeAfterTemplateFiles

  fun getDescriptionFolder(): String = descriptionFolder
}

/**
 * Resolves *description* and *before|after* template files associated with the given class and description type.
 *
 * @param epFqn FQN of associated extension point if available
 * @see org.jetbrains.idea.devkit.inspections.DescriptionType.createDescriptionTypeResolver
 */
internal sealed class DescriptionTypeResolver(
  protected val descriptionType: DescriptionType,
  protected val module: Module, protected val psiClass: PsiClass,
  @NonNls private val epFqn: String? = null,
) {

  /**
   * @return whether to skip functionality if the given class is not registered in any `plugin.xml`
   */
  abstract fun skipIfNotRegisteredInPluginXml(): Boolean

  /**
   * @return whether to skip functionality if *before|after* template files are defined as optional
   */
  open fun skipOptionalBeforeAfterTemplateFiles(): Boolean = false

  /**
   * @return associated description file or `null` if not resolved
   */
  open fun resolveDescriptionFile(): PsiFile? {
    val descriptionDirName = getDescriptionDirName() ?: return null

    // additional filename checks to force case-sensitivity
    for (description in DescriptionCheckerUtil.getDescriptionsDirs(module, descriptionType)) {
      val dir = description.findSubdirectory(descriptionDirName) ?: continue
      if (dir.getName() != descriptionDirName) continue

      val descriptionFile = dir.findFile(DESCRIPTION_HTML) ?: continue
      if (descriptionFile.name == DESCRIPTION_HTML) return descriptionFile
    }
    return null
  }

  /**
   * @return associated *before|after* template files, sorted by filename
   */
  fun resolveBeforeAfterTemplateFiles(): List<PsiFile> {
    assert(descriptionType.hasBeforeAfterTemplateFiles())

    val descriptionFile = resolveDescriptionFile() ?: return emptyList()

    val psiDirectory = descriptionFile.parent ?: return emptyList()

    val files = mutableListOf<PsiFile>()
    for (file in psiDirectory.files) {
      val name = file.name
      if (name.endsWith(".template")) {
        if (name.startsWith("before.")) {
          files.add(file)
        }
        else if (name.startsWith("after.")) {
          files.add(file)
        }
      }
    }
    files.sortWith(Comparator.comparing(PsiFileSystemItem::getName))
    return files
  }

  open fun getDescriptionDirName(): String? {
    return DescriptionCheckerUtil.getDefaultDescriptionDirName(psiClass)
  }

  protected fun processExtensions(candidates: List<ExtensionCandidate> = locateExtensionsByPsiClass(psiClass), processor: Processor<Extension?>): Boolean {
    assert(epFqn != null)
    for (candidate in candidates) {
      val extension: Extension? = DomUtil.findDomElement<Extension?>(candidate.pointer.getElement(), Extension::class.java, false)
      val extensionPoint = extension!!.getExtensionPoint()
      if (extensionPoint == null) continue

      val effectiveQualifiedName = extensionPoint.getEffectiveQualifiedName()
      if (effectiveQualifiedName == epFqn) {
        return processor.process(extension)
      }
    }

    return true
  }
}

internal class IntentionDescriptionTypeResolver(module: Module, psiClass: PsiClass) : DescriptionTypeResolver(DescriptionType.INTENTION, module, psiClass, "com.intellij.intentionAction") {

  override fun skipIfNotRegisteredInPluginXml(): Boolean {
    val candidates = locateExtensionsByPsiClass(psiClass)

    // 1. not registered at all
    if (candidates.isEmpty()) {
      return true
    }

    // 2. find registration under EP name
    return processExtensions(candidates, CommonProcessors.alwaysFalse<Extension?>())
  }

  override fun getDescriptionDirName(): String? {
    val customDirectory = Ref.create<String?>()
    processExtensions { extension ->
      val descriptionDirectoryName = DevKitDomUtil.getTag(extension, "descriptionDirectoryName")
      if (descriptionDirectoryName != null && DomUtil.hasXml(descriptionDirectoryName)) {
        customDirectory.set(descriptionDirectoryName.getStringValue())
      }
      false
    }
    if (customDirectory.get() != null) {
      return customDirectory.get()
    }

    return super.getDescriptionDirName()
  }

  override fun skipOptionalBeforeAfterTemplateFiles(): Boolean {
    return processExtensions { extension ->
      val skipBeforeAfterValue = DevKitDomUtil.getTag(extension, "skipBeforeAfter")
      return@processExtensions skipBeforeAfterValue != null && DomUtil.hasXml(skipBeforeAfterValue) &&
                               skipBeforeAfterValue.getValue() == true
    }
  }
}


internal class PostfixTemplateDescriptionTypeResolver(module: Module, psiClass: PsiClass) : DescriptionTypeResolver(DescriptionType.POSTFIX_TEMPLATES, module, psiClass) {

  override fun skipIfNotRegisteredInPluginXml(): Boolean = false
}


internal class InspectionDescriptionTypeResolver(module: Module, psiClass: PsiClass) : DescriptionTypeResolver(DescriptionType.INSPECTION, module, psiClass) {

  @NonNls
  private val INSPECTION_PROFILE_ENTRY: String = InspectionProfileEntry::class.java.getName()

  override fun skipIfNotRegisteredInPluginXml(): Boolean {
    return isAnyPathMethodOverridden(psiClass)
  }

  override fun resolveDescriptionFile(): PsiFile? {
    return InspectionDescriptionInfo.create(module, psiClass).descriptionFile
  }

  override fun getDescriptionDirName(): String? {
    return InspectionDescriptionInfo.create(module, psiClass).getFilename()
  }

  private fun isAnyPathMethodOverridden(psiClass: PsiClass?): Boolean {
    return !(isLastMethodDefinitionIn("getStaticDescription", psiClass)
             && isLastMethodDefinitionIn("getDescriptionContextClass", psiClass)
             && isLastMethodDefinitionIn("getDescriptionFileName", psiClass))
  }

  private fun isLastMethodDefinitionIn(
    methodName: String,
    psiClass: PsiClass?,
  ): Boolean {
    if (psiClass == null) return false
    for (method in psiClass.findMethodsByName(methodName, false)) {
      val containingClass = method.getContainingClass()
      if (containingClass == null) return false
      return INSPECTION_PROFILE_ENTRY == containingClass.getQualifiedName()
    }
    return isLastMethodDefinitionIn(methodName, psiClass.getSuperClass())
  }
}

