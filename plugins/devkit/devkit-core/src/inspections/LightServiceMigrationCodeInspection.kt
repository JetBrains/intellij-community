// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.PluginPlatformInfo
import org.jetbrains.idea.devkit.util.PsiUtil.isIdeaProject
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass

class LightServiceMigrationCodeInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz !is PsiClass ||
            clazz.classKind != JvmClassKind.CLASS ||
            PsiUtil.isLocalOrAnonymousClass(clazz) ||
            PsiUtil.isAbstractClass(clazz)) {
          return true
        }
        if (isIdeaProject(project) || isVersion193OrHigher(clazz) || ApplicationManager.getApplication().isUnitTestMode) {
          if (clazz.hasAnnotation(Service::class.java.canonicalName)) return true
          if (!LightServiceMigrationUtil.canBeLightService(clazz)) return true
          for (candidate in locateExtensionsByPsiClass(clazz)) {
            val extension = DomUtil.findDomElement(candidate.pointer.element, Extension::class.java, false) ?: continue
            val (serviceImplementation, level) = LightServiceMigrationUtil.getServiceImplementation(extension) ?: continue
            if (serviceImplementation == clazz) {
              val message = LightServiceMigrationUtil.getMessage(level)
              sink.highlight(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
              break
            }
          }
        }
        return true
      }

      private fun isVersion193OrHigher(aClass: PsiClass): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(aClass) ?: return false
        val buildNumber = PluginPlatformInfo.forModule(module).sinceBuildNumber
        return buildNumber != null && buildNumber.baselineVersion >= 193
      }
    }
  }
}