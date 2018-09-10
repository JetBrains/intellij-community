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
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrMethodWrapper
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class GroovyObjectTransformationSupport : AstTransformationSupport {

  companion object {
    private val ORIGIN_INFO = "via GroovyObject"
    private val KEY: Key<Boolean> = Key.create("groovy.object.method")

    private fun TransformationContext.findClass(fqn: String) = psiFacade.findClass(fqn, resolveScope)
    @JvmStatic fun isGroovyObjectSupportMethod(method: PsiMethod): Boolean = method.getUserData(KEY) == true
  }

  override fun applyTransformation(context: TransformationContext) {
    if (context.codeClass.isInterface) return
    if (context.superClass?.language == GroovyLanguage) return

    val groovyObject = context.findClass(GROOVY_OBJECT)
    if (groovyObject == null || !GrTraitUtil.isInterface(groovyObject)) return

    context.addInterface(TypesUtil.createType(groovyObject))

    val implementedMethods = groovyObject.methods.map {
      GrMethodWrapper.wrap(it).apply {
        setContext(context.codeClass)
        modifierList.removeModifier(GrModifierFlags.ABSTRACT_MASK)
        originInfo = ORIGIN_INFO
        putUserData(KEY, true)
      }
    }
    context.addMethods(implementedMethods)
  }
}