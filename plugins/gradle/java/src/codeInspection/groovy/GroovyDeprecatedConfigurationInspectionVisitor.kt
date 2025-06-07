// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.util.containers.map2Array
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.DECLARATION_ALTERNATIVES
import org.jetbrains.plugins.gradle.service.resolve.GradlePropertyExtensionsContributor
import org.jetbrains.plugins.groovy.intentions.GrReplaceMethodCallQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GroovyDeprecatedConfigurationInspectionVisitor(val holder: ProblemsHolder): GroovyElementVisitor() {

  override fun visitReferenceExpression(referenceExpression: GrReferenceExpression) {
    val referenceNameElement = referenceExpression.referenceNameElement ?: return
    val resolved = referenceExpression.resolve() ?: return
    val alternatives = resolved.getUserData(DECLARATION_ALTERNATIVES)?.takeIf { it.isNotEmpty() } ?: return

    val knownConfigurations = GradlePropertyExtensionsContributor.getExtensionsFor(referenceExpression)?.configurations?.keys ?: emptyList()

    val descriptor = InspectionManager.getInstance(referenceExpression.project)
      .createProblemDescriptor(
        referenceNameElement,
        GradleInspectionBundle.message("inspection.message.configuration.is.deprecated", referenceNameElement.text),
        false,
        alternatives.filter { it in knownConfigurations }.map2Array { GrReplaceMethodCallQuickFix(referenceNameElement.text, it) },
        ProblemHighlightType.LIKE_DEPRECATED)

    holder.registerProblem(descriptor)
  }
}