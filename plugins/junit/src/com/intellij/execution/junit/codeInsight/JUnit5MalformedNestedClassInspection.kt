// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.execution.JUnitBundle
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.openapi.project.Project
import com.siyeh.ig.junit.JUnitCommonClassNames

class JUnit5MalformedNestedClassInspection : JvmLocalInspection() {
  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz.containingClass != null &&
            clazz.hasModifier(JvmModifier.STATIC) &&
            clazz.hasAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED)) {

          val fixes = createModifierActions(clazz, modifierRequest(JvmModifier.STATIC, false)).toTypedArray()
          sink.highlight(JUnitBundle.message("junit5.malformed.nested.class.inspection.description"),
                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         *IntentionWrapper.wrapToQuickFixes(fixes, clazz.sourceElement!!.containingFile))
        }
        return true
      }
    }
  }
}