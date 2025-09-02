// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.highlighting.RemoveDomElementQuickFix
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.getProjectLevelFQN

internal class ConvertToLightServiceFix private constructor(private val classPointer: SmartPsiElementPointer<PsiElement>,
                                                            private val xmlTagPointer: SmartPsiElementPointer<XmlTag>,
                                                            private val level: Service.Level) : LocalQuickFix {

  constructor(aClass: PsiElement, xmlTag: XmlTag, level: Service.Level) : this(aClass.createSmartPointer(),
                                                                               xmlTag.createSmartPointer(),
                                                                               level)

  override fun getFamilyName(): String {
    return DevKitBundle.message("inspection.light.service.migration.family.name")
  }

  override fun getName(): String {
    val key = when (level) {
      Service.Level.APP -> "inspection.light.service.migration.app.level.fix"
      Service.Level.PROJECT -> "inspection.light.service.migration.project.level.fix"
    }
    return DevKitBundle.message(key)
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val aClass = classPointer.element ?: return
    val xmlTag = xmlTagPointer.element ?: return
    val addServiceAnnotationProvider = AddServiceAnnotationProviders.forLanguage(aClass.language) ?: return
    addServiceAnnotationProvider.addServiceAnnotation(aClass, level)
    RemoveDomElementQuickFix.removeXmlTag(xmlTag, project)
  }
}

private val EP_NAME: ExtensionPointName<AddServiceAnnotationProvider> =
  ExtensionPointName.create("DevKit.lang.addServiceAnnotationProvider")

internal object AddServiceAnnotationProviders : LanguageExtension<AddServiceAnnotationProvider>(EP_NAME.name)

@ApiStatus.Internal
@IntellijInternalApi
interface AddServiceAnnotationProvider {
  fun addServiceAnnotation(aClass: PsiElement, level: Service.Level)
}

internal class JavaAddServiceAnnotationProvider : AddServiceAnnotationProvider {
  override fun addServiceAnnotation(aClass: PsiElement, level: Service.Level) {
    if (aClass !is PsiClass) return
    val attributes = when (level) {
      Service.Level.APP -> emptyArray()
      Service.Level.PROJECT -> {
        val factory = JavaPsiFacade.getElementFactory(aClass.project)
        val projectLevelFqn = getProjectLevelFQN()
        val newAnnotation = factory.createAnnotationFromText("@${Service::class.java.canonicalName}(${projectLevelFqn})", aClass)
        newAnnotation.parameterList.attributes
      }
    }
    val annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(Service::class.java.canonicalName, attributes, aClass.modifierList!!)
    if (annotation != null) {
      JavaCodeStyleManager.getInstance(aClass.project).shortenClassReferences(annotation)
    }
  }
}