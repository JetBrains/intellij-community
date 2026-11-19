// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmParameter
import com.intellij.lang.jvm.actions.constantAttribute
import com.intellij.lang.jvm.actions.createChangeAnnotationAttributeActions
import com.intellij.lang.jvm.actions.createChangeParametersActions
import com.intellij.lang.jvm.actions.setMethodParametersRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.workspace.ProjectWorkspace
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiType
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.devkit.DevKitBundle

internal class MismatchedLightServiceLevelAndCtorInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitMethod(method: JvmMethod): Boolean {
        if (!method.isConstructor) return true
        val clazz = method.containingClass ?: return true
        if (clazz.classKind != JvmClassKind.CLASS ||
            clazz.hasModifier(JvmModifier.ABSTRACT) ||
            clazz.isLocalOrAnonymous()) {
          return true
        }
        val annotation = clazz.getAnnotation(Service::class.java.canonicalName) ?: return true
        val sourceElement = method.sourceElement ?: return true
        val file = sourceElement.containingFile ?: return true
        val levelType = getLevelType(annotation, sourceElement.language)
        if (!levelType.isProject() && isProjectLevelExclusiveCtor(method)) {
          val elementToReport = (annotation as? PsiAnnotation)?.nameReferenceElement ?: return true
          registerProblemLevelRequired(annotation, elementToReport, file, getProjectLevelFQN(),
                                       DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.project.level.required"),
                                       DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.specify.project.level.fix"))
        }
        if (levelType != LevelType.WORKSPACE && levelType != LevelType.NOT_SPECIFIED && isWorkspaceLevelExclusiveCtor(method)) {
          val elementToReport = (annotation as? PsiAnnotation)?.nameReferenceElement ?: return true
          registerProblemLevelRequired(annotation, elementToReport, file, getLevelFQN(Service.Level.WORKSPACE),
                                       DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.workspace.level.required"),
                                       DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.specify.workspace.level.fix"))
        }
        if (levelType.isApp() && !isAppLevelServiceCtor(method) && !hasAppLevelServiceCtor(clazz)) {
          registerProblemApplicationLevelRequired(method, file)
        }
        if (levelType == LevelType.WORKSPACE && !isWorkspaceLevelServiceCtor(method) && !hasWorkspaceLevelServiceCtor(clazz)) {
          sink.highlight(DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.workspace.level.ctor.required"))
        }
        return true
      }

      private fun JvmClass.isLocalOrAnonymous(): Boolean {
        return this.qualifiedName == null
      }

      private fun hasAppLevelServiceCtor(clazz: JvmClass): Boolean {
        return clazz.methods.filter { it.isConstructor }.any { isAppLevelServiceCtor(it) }
      }

      /**
       * Check if the given [constructor] is suitable for an application-level service.
       */
      private fun isAppLevelServiceCtor(constructor: JvmMethod): Boolean {
        assert(constructor.isConstructor)
        return !constructor.hasParameters() || (constructor.parameters.singleOrNull()?.hasType(CoroutineScope::class.java) ?: false)
      }

      /**
       * Checks if the given [constructor] requires that the light service class be specified as `@Service(Service.Level.PROJECT)`.
       */
      private fun isProjectLevelExclusiveCtor(constructor: JvmMethod): Boolean {
        assert(constructor.isConstructor)
        val parameters = constructor.parameters
        return when (parameters.size) {
          1 -> parameters[0].hasType(Project::class.java)
          2 -> parameters[0].hasType(Project::class.java) && parameters[1].hasType(CoroutineScope::class.java)
          else -> false
        }
      }

      private fun hasWorkspaceLevelServiceCtor(clazz: JvmClass): Boolean {
        return clazz.methods.filter { it.isConstructor }.any { isWorkspaceLevelServiceCtor(it) }
      }

      /**
       * Checks if the given [constructor] is suitable for a workspace-level service.
       */
      private fun isWorkspaceLevelServiceCtor(constructor: JvmMethod): Boolean {
        return isAppLevelServiceCtor(constructor) || isWorkspaceLevelExclusiveCtor(constructor)
      }

      /**
       * Checks if the given [constructor] requires that the light service class be specified as `@Service(Service.Level.WORKSPACE)`.
       */
      private fun isWorkspaceLevelExclusiveCtor(constructor: JvmMethod): Boolean {
        assert(constructor.isConstructor)
        val parameters = constructor.parameters
        return when (parameters.size) {
          1 -> parameters[0].hasType(ProjectWorkspace::class.java)
          2 -> parameters[0].hasType(ProjectWorkspace::class.java) && parameters[1].hasType(CoroutineScope::class.java)
          else -> false
        }
      }

      private fun JvmParameter.hasType(clazz: Class<*>): Boolean {
        return (this.type as? PsiType)?.canonicalText == clazz.canonicalName
      }

      private fun registerProblemLevelRequired(annotation: JvmAnnotation,
                                               elementToReport: PsiJavaCodeReferenceElement,
                                               file: PsiFile,
                                               levelFqn: String,
                                               @InspectionMessage message: String,
                                               @IntentionName fixText: String) {
        val request = constantAttribute(DEFAULT_REFERENCED_METHOD_NAME, levelFqn)
        val actions = createChangeAnnotationAttributeActions(annotation, 0, request, fixText, fixText)
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
        val holder = (sink as HighlightSinkImpl).holder
        holder.registerProblem(elementToReport, message, *fixes)
      }

      private fun registerProblemApplicationLevelRequired(method: JvmMethod, file: PsiFile) {
        val actions = createChangeParametersActions(method, setMethodParametersRequest(emptyList()))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
        val message = DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.app.level.ctor.required")
        sink.highlight(message, *fixes)
      }
    }
  }
}
