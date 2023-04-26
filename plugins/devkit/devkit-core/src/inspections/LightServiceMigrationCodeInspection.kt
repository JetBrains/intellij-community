// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass

internal class LightServiceMigrationCodeInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz !is PsiClass ||
            clazz.classKind != JvmClassKind.CLASS ||
            PsiUtil.isLocalOrAnonymousClass(clazz) ||
            PsiUtil.isAbstractClass(clazz)) {
          return true
        }
        if (LightServiceMigrationUtil.isVersion193OrHigher(clazz) ||
            ApplicationManager.getApplication().isUnitTestMode) {
          if (clazz.hasAnnotation(Service::class.java.canonicalName)) return true
          if (!clazz.hasModifier(JvmModifier.FINAL)) return true
          for (candidate in locateExtensionsByPsiClass(clazz)) {
            val extension = DomUtil.findDomElement(candidate.pointer.element, Extension::class.java, false) ?: continue
            val (serviceImplementation, level) = LightServiceMigrationUtil.getServiceImplementation(extension) ?: continue
            if (level == Service.Level.APP &&
                JvmInheritanceUtil.isInheritor(clazz, PersistentStateComponent::class.java.canonicalName)) {
              continue
            }
            if (serviceImplementation == clazz) {
              val message = LightServiceMigrationUtil.getMessage(level)
              sink.highlight(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
              break
            }
          }
        }
        return true
      }
    }
  }
}