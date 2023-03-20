// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.DevKitBundle

internal class LightServiceMustBeFinalInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz.classKind != JvmClassKind.CLASS || clazz.hasModifier(JvmModifier.FINAL)) return true
        val file = clazz.sourceElement?.containingFile ?: return true
        val hasServiceAnnotation = clazz.hasAnnotation(Service::class.java.canonicalName)
        if (hasServiceAnnotation) {
          val actions = createModifierActions(clazz, modifierRequest(JvmModifier.FINAL, true))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
          sink.highlight(DevKitBundle.message("inspection.light.service.must.be.final.message"), ProblemHighlightType.GENERIC_ERROR, *fixes)
        }
        return true
      }
    }
  }
}
