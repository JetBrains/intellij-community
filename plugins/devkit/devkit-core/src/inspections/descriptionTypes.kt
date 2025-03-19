// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlAttribute
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

@NonNls
internal const val INTENTION_DESCRIPTION_DIRECTORY_NAME = "descriptionDirectoryName"

@NonNls
internal const val INTENTION_ACTION_EP = "com.intellij.intentionAction"

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

  /**
   * @return description directories (usually exactly one)
   */
  fun getDescriptionFolderDirs(module: Module): Array<PsiDirectory> {
    val javaPsiFacade = JavaPsiFacade.getInstance(module.getProject())
    val psiPackage = javaPsiFacade.findPackage(getDescriptionFolder()) ?: return PsiDirectory.EMPTY_ARRAY

    val currentModuleDirectories = psiPackage.getDirectories(module.getModuleScope(false))
    return when {
      currentModuleDirectories.size != 0 -> currentModuleDirectories
      else -> psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module))
    }
  }
}

/**
 * Additional user data provided by [DescriptionTypeResolver] implementations.
 */
internal object DescriptionTypeResolverKeys {

  /**
   * Overridden `getShortName()` method in inspection.
   */
  @JvmField
  internal val INSPECTION_SHORT_NAME_METHOD = Key.create<PsiMethod?>("INSPECTION_SHORT_NAME_METHOD")

  /**
   * Short name provided via `plugin.xml`.
   */
  @JvmField
  internal val INSPECTION_SHORT_NAME_IN_XML = Key.create<Boolean>("INSPECTION_SHORT_NAME_IN_XML")

  /**
   * [XmlAttribute] of 'shortName' attribute ([INSPECTION_SHORT_NAME_IN_XML]=true)
   */
  @JvmField
  internal val INSPECTION_SHORT_NAME_XML_ATTRIBUTE = Key.create<XmlAttribute>("INSPECTION_SHORT_NAME_XML_ATTRIBUTE")
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
) : UserDataHolder by UserDataHolderBase() {

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
    for (description in descriptionType.getDescriptionFolderDirs(module)) {
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
    return getDefaultDescriptionDirName(psiClass)
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

  companion object {

    @JvmStatic
    fun getDefaultDescriptionDirName(psiClass: PsiClass): String? {
      var descriptionDir = ""
      var each: PsiClass? = psiClass
      while (each != null) {
        val name = each.getName()
        if (name.isNullOrBlank()) {
          return null
        }
        descriptionDir = name + descriptionDir
        each = each.getContainingClass()
      }
      return descriptionDir
    }
  }
}


private class IntentionDescriptionTypeResolver(module: Module, psiClass: PsiClass) : DescriptionTypeResolver(DescriptionType.INTENTION, module, psiClass, INTENTION_ACTION_EP) {

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
      val descriptionDirectoryName = DevKitDomUtil.getTag(extension, INTENTION_DESCRIPTION_DIRECTORY_NAME)
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


private class PostfixTemplateDescriptionTypeResolver(module: Module, psiClass: PsiClass) : DescriptionTypeResolver(DescriptionType.POSTFIX_TEMPLATES, module, psiClass) {

  override fun skipIfNotRegisteredInPluginXml(): Boolean = false
}


private class InspectionDescriptionTypeResolver(module: Module, psiClass: PsiClass) : DescriptionTypeResolver(DescriptionType.INSPECTION, module, psiClass) {

  private val inspectionDescriptionInfo: InspectionDescriptionInfo = InspectionDescriptionInfo.create(module, psiClass)

  init {
    putUserData(DescriptionTypeResolverKeys.INSPECTION_SHORT_NAME_METHOD, inspectionDescriptionInfo.shortNameMethod)
    putUserData(DescriptionTypeResolverKeys.INSPECTION_SHORT_NAME_IN_XML, inspectionDescriptionInfo.isShortNameInXml)
    putUserData(DescriptionTypeResolverKeys.INSPECTION_SHORT_NAME_XML_ATTRIBUTE, inspectionDescriptionInfo.shortNameXmlAttribute)
  }

  @NonNls
  private val INSPECTION_PROFILE_ENTRY: String = InspectionProfileEntry::class.java.getName()

  override fun skipIfNotRegisteredInPluginXml(): Boolean {
    return isAnyPathMethodOverridden(psiClass)
  }

  override fun resolveDescriptionFile(): PsiFile? {
    return inspectionDescriptionInfo.descriptionFile
  }

  override fun getDescriptionDirName(): String? {
    return inspectionDescriptionInfo.filename
  }

  private fun isAnyPathMethodOverridden(psiClass: PsiClass): Boolean {
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

