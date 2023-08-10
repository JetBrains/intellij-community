// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.createRemoveAnnotationActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.idea.devkit.DevKitBundle

internal class LightServiceMustBeFinalInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        val sourceElement = clazz.sourceElement
        if (sourceElement !is PsiClass) return true
        if (sourceElement.isAnnotationType || sourceElement.isEnum || sourceElement.hasModifier(JvmModifier.FINAL)) return true
        val file = sourceElement.containingFile ?: return true
        val serviceAnnotation = sourceElement.getAnnotation(Service::class.java.canonicalName) ?: return true
        val elementToReport = serviceAnnotation.nameReferenceElement ?: return true
        if (sourceElement.isInterface || sourceElement.hasModifier(JvmModifier.ABSTRACT)) {
          val actions = createRemoveAnnotationActions(sourceElement, annotationRequest(Service::class.java.canonicalName))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
          val message = DevKitBundle.message("inspection.light.service.must.be.concrete.class.message")
          val holder = (sink as HighlightSinkImpl).holder
          holder.registerProblem(elementToReport, message, ProblemHighlightType.GENERIC_ERROR, *fixes)
        }
        else {
          val errorMessageProvider = getProvider(LightServiceMustBeFinalErrorMessageProviders, sourceElement.language) ?: return true
          val message = errorMessageProvider.provideErrorMessage()
          val actions = createModifierActions(sourceElement, modifierRequest(JvmModifier.FINAL, true))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
          sink.highlight(message, ProblemHighlightType.GENERIC_ERROR, *fixes)
        }
        return true
      }
    }
  }
}
