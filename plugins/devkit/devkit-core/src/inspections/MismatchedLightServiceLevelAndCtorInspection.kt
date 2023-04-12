// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.devkit.DevKitBundle

internal class MismatchedLightServiceLevelAndCtorInspection : DevKitJvmInspection() {

  private val COROUTINE_SCOPE_PARAM_NAME = "scope"

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitMethod(method: JvmMethod): Boolean {
        if (!method.isConstructor) return true
        val sourceElement = method.sourceElement ?: return true
        val file = sourceElement.containingFile ?: return true
        val containingClass = method.containingClass ?: return true
        val annotation = containingClass.getAnnotation(Service::class.java.canonicalName) ?: return true
        val elementToReport = (annotation as? PsiAnnotation)?.nameReferenceElement ?: return true
        val levelType = ServiceUtil.getLevelType(annotation, sourceElement.language.id == "kotlin")
        if (!levelType.isProject() && isProjectParamCtor(method)) {
          registerProblemProjectLevelRequired(annotation, elementToReport, file)
        }
        if (levelType.isApp() && !isAppLevelServiceCtor(method) && !hasAppLevelServiceCtor(containingClass)) {
          registerProblemApplicationLevelRequired(method, file)
        }
        return true
      }

      private fun hasAppLevelServiceCtor(clazz: JvmClass): Boolean {
        return clazz.methods.filter { it.isConstructor }.any { isAppLevelServiceCtor(it) }
      }

      private fun isAppLevelServiceCtor(method: JvmMethod): Boolean {
        assert(method.isConstructor)
        return method.parameters.isEmpty() || method.hasSingleParamOfType(CoroutineScope::class.java)
      }

      private fun isProjectParamCtor(method: JvmMethod): Boolean {
        assert(method.isConstructor)
        return method.hasSingleParamOfType(Project::class.java)
      }

      private fun JvmMethod.hasSingleParamOfType(clazz: Class<*>): Boolean {
        return (this.parameters.singleOrNull()?.type as? PsiType)?.canonicalText == clazz.canonicalName
      }

      private fun registerProblemProjectLevelRequired(annotation: JvmAnnotation,
                                                      elementToReport: PsiJavaCodeReferenceElement,
                                                      file: PsiFile) {
        val projectLevelFqn = "${Service.Level::class.java.canonicalName}.${Service.Level.PROJECT}"
        val request = constantAttribute(DEFAULT_REFERENCED_METHOD_NAME, projectLevelFqn)
        val actions = createChangeAnnotationAttributeActions(annotation, 0, request)
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
        val holder = (sink as HighlightSinkImpl).holder
        holder.registerProblem(elementToReport,
                               DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.project.level.required"),
                               *fixes)
      }

      private fun registerProblemApplicationLevelRequired(method: JvmMethod, file: PsiFile) {
        val elementFactory = PsiElementFactory.getInstance(project)
        val coroutineScopeType = elementFactory.createTypeByFQClassName(CoroutineScope::class.java.canonicalName,
                                                                        GlobalSearchScope.allScope(project))
        val methodParametersRequest: ChangeParametersRequest = setMethodParametersRequest(
          linkedMapOf(COROUTINE_SCOPE_PARAM_NAME to coroutineScopeType).entries)
        val actions = createChangeParametersActions(method, setMethodParametersRequest(emptyList())) + createChangeParametersActions(
          method, methodParametersRequest)
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
        val message = DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.app.level.ctor.required")
        sink.highlight(message, *fixes)
      }
    }
  }
}
