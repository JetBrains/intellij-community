// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.resolve.impl.getAllConstructors

/**
 * Given a collection on the right hand side checks if the class on the left has collection constructor.
 * ```
 * class C {
 *   C(Collection a) {}
 * }
 * C c = new ArrayList()
 * ```
 * @see org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.continueCastOnSAM
 */
class GrCollectionConstructorConverter : GrTypeConverter() {

  override fun isConvertible(targetType: PsiType, actualType: PsiType, position: Position, context: GroovyPsiElement): ConversionResult? {
    if (position != Position.ASSIGNMENT && position != Position.RETURN_VALUE || isCompileStatic(context)) {
      return null
    }
    if (!isInheritor(actualType, JAVA_UTIL_COLLECTION)) {
      return null
    }
    val constructedType = targetType as? PsiClassType ?: return null
    val constructedClass = constructedType.resolve() ?: return null
    val hasConstructor = hasCollectionApplicableConstructor(constructedClass, context)
    return if (hasConstructor) ConversionResult.OK else null
  }

  companion object {

    fun hasCollectionApplicableConstructor(constructedClass: PsiClass, context: PsiElement): Boolean {
      val constructors = getAllConstructors(constructedClass, context)
      if (constructors.isEmpty()) {
        return false
      }
      val collectionType = createType(JAVA_UTIL_COLLECTION, context)
      return constructors.any {
        val parameter = it.parameterList.parameters.singleOrNull()
        parameter?.type?.isAssignableFrom(collectionType) ?: false
      }
    }
  }
}
