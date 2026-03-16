// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.internal.statistic.eventLog.FUS_RECORDER
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns.psiMethod
import com.intellij.patterns.uast.callExpression
import com.intellij.psi.PsiManager
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.toUElementOfType

internal const val EVENT_LOG_GROUP_FQN = "com.intellij.internal.statistic.eventLog.EventLogGroup"
internal const val EVENT_LOG_PROPERTIES_DIR = "build/events"
internal const val REGISTER_EVENT_NAME = "registerEvent"
internal const val REGISTER_VARARG_EVENT_NAME = "registerVarargEvent"
internal const val REGISTER_ACTIVITY_NAME = "registerIdeActivity"

internal fun eventLogGroupCall(): ElementPattern<UCallExpression> = callExpression().andOr(
  callExpression().constructor(EVENT_LOG_GROUP_FQN),
  callExpression().withAnyResolvedMethod(
    psiMethod().withName(REGISTER_EVENT_NAME, REGISTER_VARARG_EVENT_NAME, REGISTER_ACTIVITY_NAME).definedInClass(EVENT_LOG_GROUP_FQN)
  )
)

internal fun findRecorderName(call: UCallExpression): String? {
  val recorderArg = call.getArgumentForParameter(2) ?: return FUS_RECORDER
  return recorderArg.evaluateString()?.takeIf { it.isNotBlank() }
}

internal fun findGroupIdAndRecorderName(expression: UExpression): Pair<String, String>? = when (expression) {
  is UCallExpression if (expression.kind == UastCallKind.CONSTRUCTOR_CALL) -> {
    val groupId = expression.getArgumentForParameter(0)?.let(UExpression::evaluateString) ?: return null
    val recorder = findRecorderName(expression) ?: return null
    groupId to recorder
  }
  is UQualifiedReferenceExpression -> {
    findGroupIdAndRecorderName(expression.selector)
  }
  else -> {
    val variable = when (val resolved = (expression as? UResolvable)?.resolveToUElement()) {
      is UVariable -> resolved
      is UMethod -> resolved.sourcePsi.toUElementOfType<UVariable>()  // Kotlin synthetic accessors
      else -> null
    } ?: return null
    val initializer = variable.uastInitializer ?: return null
    findGroupIdAndRecorderName(initializer)
  }
}

internal fun findEventLogPropertiesFile(project: Project, recorder: String): PropertiesFile? {
  val projectDir = project.guessProjectDir() ?: return null
  val file = projectDir.findFileByRelativePath("${EVENT_LOG_PROPERTIES_DIR}/${recorder}.properties") ?: return null
  return PsiManager.getInstance(project).findFile(file) as? PropertiesFile
}
