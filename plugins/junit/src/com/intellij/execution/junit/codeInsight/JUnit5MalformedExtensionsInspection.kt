// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.execution.JUnitBundle
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmField
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.junit.JUnitCommonClassNames

class JUnit5MalformedExtensionsInspection : JvmLocalInspection() {
  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitField(field: JvmField): Boolean {
        if (field.hasAnnotation(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION) && field.type is PsiType) {
          val psiType = field.type as PsiType
          if (!InheritanceUtil.isInheritor(psiType, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION)) {
            sink.highlight(JUnitBundle.message("junit5.malformed.extension.registration.message", psiType.canonicalText, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION))
          }
          else if (!field.hasModifier(JvmModifier.STATIC) &&
                   (InheritanceUtil.isInheritor(psiType, "org.junit.jupiter.api.extension.BeforeAllCallback") || InheritanceUtil.isInheritor(psiType, "org.junit.jupiter.api.extension.AfterAllCallback"))) {
            val fixes = createModifierActions(field, modifierRequest(JvmModifier.STATIC, true)).toTypedArray()
            sink.highlight(JUnitBundle.message("junit5.malformed.extension.class.level.message", psiType.presentableText), *IntentionWrapper.wrapToQuickFixes(fixes, field.sourceElement!!.containingFile))
          }
        }
        return true
      }
    }
  }
}