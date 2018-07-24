// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.lang.jvm.JvmMember
import com.intellij.lang.jvm.JvmMethod
import com.intellij.model.Symbol
import com.intellij.model.search.OccurrenceSearchRequestor
import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.plugins.groovy.findUsages.GroovyScopeUtil.restrictScopeToGroovyFiles
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyNameAndKind

class GrAliasedImportedElementSearchRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector) {
    val parameters = collector.parameters
    val target = parameters.target as? JvmMember ?: return

    val name = runReadAction { target.name }
    if (name == null || StringUtil.isEmptyOrSpaces(name)) return

    val groovyScope = restrictScopeToGroovyFiles(parameters.effectiveSearchScope)
    collector.searchWord(name).inScope(groovyScope).setTargetHint(target).searchRequests(MyProcessor(target, null))
    if (target is JvmMethod) {
      val (propertyName, kind) = runReadAction { getPropertyNameAndKind(target) } ?: return
      collector.searchWord(propertyName).inScope(groovyScope).setTargetHint(target).searchRequests(MyProcessor(target, kind.prefix))
    }
  }

  private class MyProcessor(private val myTarget: Symbol, private val prefix: String?) : OccurrenceSearchRequestor {

    override fun collectRequests(collector: SearchRequestCollector, element: PsiElement, offsetInElement: Int) {
      if (offsetInElement != 0) return

      val codeReference = element.parent as? GrCodeReferenceElement ?: return
      if (codeReference.referenceNameElement !== element) return

      val importStatement = codeReference.parent as? GrImportStatement ?: return
      if (!importStatement.isAliasedImport) return

      val alias = importStatement.importedName ?: return

      if (!codeReference.references((myTarget as? GrAccessorMethod)?.property ?: myTarget)) return

      val scope = LocalSearchScope(element.containingFile)
      collector.searchWord(alias).inScope(scope).search(myTarget)
      if (prefix != null) {
        collector.searchWord(prefix + GroovyPropertyUtils.capitalize(alias)).inScope(scope).search(myTarget)
      }
    }
  }
}
