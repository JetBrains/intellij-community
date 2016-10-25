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
import com.intellij.psi.impl.light.LightVariableBuilder
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

class LogbackAliasedImportedMemberContributor : NonCodeMembersContributor() {

  private companion object {
    val importedConstants = listOf("off", "error", "warn", "info", "debug", "trace", "all")
    val levelFqn = "ch.qos.logback.classic.Level"
  }

  override fun getParentClassName() = "logback"

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (!aClass.isLogbackConfig()) return
    if (!ResolveUtil.shouldProcessProperties(processor.getHint(ElementClassHint.KEY))) return

    val levelClass = JavaPsiFacade.getInstance(place.project).findClass(levelFqn, place.resolveScope) ?: return

    fun process(constantName: String): Boolean {
      val field = levelClass.findFieldByName(constantName.toUpperCase(), false) ?: return true
      val variable = LightVariableBuilder<LightVariableBuilder<*>>(constantName, field.type, field)
      return processor.execute(variable, state)
    }

    val name = processor.getHint(NameHint.KEY)?.getName(state)

    if (name != null) {
      if (name in importedConstants) {
        process(name)
      }
    }
    else {
      for (constantName in importedConstants) {
        if (!process(constantName)) return
      }
    }
  }
}