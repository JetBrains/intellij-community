// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.MemberRequest
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.openapi.project.Project
import com.siyeh.ig.junit.JUnitCommonClassNames

class JUnit5MalformedNestedClassInspection : JvmLocalInspection() {
  override fun buildVisitor(project: Project, sink: JvmLocalInspection.HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean>? {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz.containingClass != null &&
            clazz.hasModifier(JvmModifier.STATIC) &&
            clazz.hasAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED)) {

          val fixes = createModifierActions(clazz, MemberRequest.Modifier(JvmModifier.STATIC, false)).toTypedArray()
          sink.highlight("Only non-static nested classes can serve as @Nested test classes.",
                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         *IntentionWrapper.wrapToQuickFixes(fixes, clazz.sourceElement!!.containingFile))
        }
        return true
      }
    }
  }
}