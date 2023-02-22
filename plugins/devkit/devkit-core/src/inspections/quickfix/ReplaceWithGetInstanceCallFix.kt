// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.generate.UastCodeGenerationPlugin.Companion.byLanguage
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.toUElement

class ReplaceWithGetInstanceCallFix(private val serviceName: String,
                                    private val methodName: String,
                                    private val isApplicationLevelService: Boolean) : LocalQuickFix {

  override fun getFamilyName(): String = DevKitBundle.message("inspection.retrieving.light.service.replace.with", serviceName, methodName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val oldCall = descriptor.psiElement.toUElement() as? UQualifiedReferenceExpression ?: return
    val generationPlugin = byLanguage(descriptor.psiElement.language) ?: return
    val factory = generationPlugin.getElementFactory(project)
    val serviceName = oldCall.getExpressionType()?.canonicalText ?: return
    val parameters = if (isApplicationLevelService) emptyList() else listOf(oldCall.receiver)
    val newCall = factory.createCallExpression(receiver = factory.createQualifiedReference(serviceName, null),
                                               methodName = methodName,
                                               parameters = parameters,
                                               expectedReturnType = oldCall.getExpressionType(),
                                               kind = UastCallKind.METHOD_CALL,
                                               context = null) ?: return
    oldCall.replace(newCall)
  }
}