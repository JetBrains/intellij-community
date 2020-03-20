// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.impl.light.LightVariableBuilder
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

class LogbackTopLevelMemberContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (!aClass.isLogbackConfig()) return

    val scope = place.resolveScope

    if (ResolveUtil.shouldProcessProperties(processor.getHint(ElementClassHint.KEY))) {
      if (processor.getHint(NameHint.KEY)?.getName(state).let { it == null || it == "hostname" }) {
        val variable = LightVariableBuilder<LightVariableBuilder<*>>(
          place.manager, "hostname", TypesUtil.createType(JAVA_LANG_STRING, place), GroovyLanguage
        )
        if (!processor.execute(variable, state)) return
      }
    }

    JavaPsiFacade.getInstance(place.project).findClass(configDelegateFqn, scope)?.processDeclarations(processor, state, null, place)
  }
}
