// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrMethodWrapper
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT
import org.jetbrains.plugins.groovy.lang.psi.util.getPOJO
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class GroovyObjectTransformationSupport : AstTransformationSupport {

  companion object {
    @NonNls private const val ORIGIN_INFO = "via GroovyObject"
    private val KEY: Key<Boolean> = Key.create("groovy.object.method")

    private fun TransformationContext.findClass(fqn: String) = psiFacade.findClass(fqn, resolveScope)
    @JvmStatic fun isGroovyObjectSupportMethod(method: PsiMethod): Boolean = method.getUserData(KEY) == true
  }

  override fun applyTransformation(context: TransformationContext) {
    if (context.codeClass.isInterface) return
    if (context.superClass?.language == GroovyLanguage) return
    if (getPOJO(context.codeClass) != null) return

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