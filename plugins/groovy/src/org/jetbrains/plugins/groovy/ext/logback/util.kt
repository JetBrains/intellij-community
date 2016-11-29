/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
