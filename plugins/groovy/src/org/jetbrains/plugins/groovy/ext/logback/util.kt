// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns.groovyLiteralExpression
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.imports.*

internal val configName = "logback.groovy"
internal val configDelegateFqn = "ch.qos.logback.classic.gaffer.ConfigurationDelegate"
internal val componentDelegateFqn = "ch.qos.logback.classic.gaffer.ComponentDelegate"
internal val appenderMethodPattern = psiMethod(configDelegateFqn, "appender")
internal val appenderDeclarationPattern = groovyLiteralExpression().methodCallParameter(0, appenderMethodPattern)

internal fun PsiClass?.isLogbackConfig() = this is GroovyScriptClass && this.containingFile.isLogbackConfig()

internal fun PsiFile?.isLogbackConfig() = this?.originalFile?.virtualFile?.name == configName

internal fun PsiElement.isBefore(other: PsiElement) = textRange.startOffset < other.textRange.startOffset

internal val PsiFile.appenderDeclarations: Map<String, GrLiteral> get() = CachedValuesManager.getCachedValue(this) {
  Result.create(computeAppenderDeclarations(), this)
}

private fun PsiFile.computeAppenderDeclarations(): Map<String, GrLiteral> {
  val result = mutableMapOf<String, GrLiteral>()
  val visitor = object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(currentElement: PsiElement?) {
      super.visitElement(currentElement)
      if (appenderDeclarationPattern.accepts(currentElement)) {
        val literal = currentElement as GrLiteral
        val literalValue = literal.value as? String ?: return
        if (!result.containsKey(literalValue)) result.put(literalValue, literal)
      }
    }
  }
  accept(visitor)
  return result
}

/**
 * see ch.qos.logback.classic.gaffer.GafferConfigurator#importCustomizer()
 */
internal fun buildImports(): List<GroovyImport> {
  val packageImports = arrayOf(
    "ch.qos.logback.core",
    "ch.qos.logback.core.encoder",
    "ch.qos.logback.core.read",
    "ch.qos.logback.core.rolling",
    "ch.qos.logback.core.status",
    "ch.qos.logback.classic.net"
  )
  val levelFqn = "ch.qos.logback.classic.Level"
  val levels = arrayOf("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL")

  return mutableListOf<GroovyImport>().apply {
    packageImports.mapTo(this, ::StarImport)
    this += RegularImport("ch.qos.logback.classic.encoder.PatternLayoutEncoder")
    this += StaticStarImport(levelFqn)
    levels.mapTo(this) {
      StaticImport(levelFqn, it, it.toLowerCase())
    }
  }
}
