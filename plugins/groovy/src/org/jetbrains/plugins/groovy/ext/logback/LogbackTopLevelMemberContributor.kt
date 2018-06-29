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

  override fun getParentClassName(): String = "logback"

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