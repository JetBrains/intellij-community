// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.createRemoveAnnotationActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.idea.devkit.DevKitBundle

internal class LightServiceMustBeFinalInspection : DevKitJvmInspection.ForClass() {

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (psiClass.isAnnotationType || psiClass.isEnum || psiClass.hasModifier(JvmModifier.FINAL)) return
    val file = psiClass.containingFile ?: return
    val serviceAnnotation = psiClass.getAnnotation(Service::class.java.canonicalName) ?: return
    val elementToReport = serviceAnnotation.nameReferenceElement ?: return
    if (psiClass.isInterface || psiClass.hasModifier(JvmModifier.ABSTRACT)) {
      val actions = createRemoveAnnotationActions(psiClass, annotationRequest(Service::class.java.canonicalName))
      val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
      val message = DevKitBundle.message("inspection.light.service.must.be.concrete.class.message")
      val holder = (sink as HighlightSinkImpl).holder
      holder.registerProblem(elementToReport, message, ProblemHighlightType.GENERIC_ERROR, *fixes)
      return
    }

    val errorMessageProvider = getProvider(LightServiceMustBeFinalErrorMessageProviders, psiClass.language) ?: return
    val message = errorMessageProvider.provideErrorMessage()
    val actions = createModifierActions(psiClass, modifierRequest(JvmModifier.FINAL, true))
    val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
    sink.highlight(message, ProblemHighlightType.GENERIC_ERROR, *fixes)
  }
}
