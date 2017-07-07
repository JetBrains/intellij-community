/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.sam

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.MethodSignature
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil.isTrait

fun findSingleAbstractMethod(clazz: PsiClass): PsiMethod? = findSingleAbstractSignatureCached(clazz)?.method

fun findSingleAbstractSignature(clazz: PsiClass): MethodSignature? = findSingleAbstractSignatureCached(clazz)

private fun findSingleAbstractSignatureCached(clazz: PsiClass): HierarchicalMethodSignature? {
  return CachedValuesManager.getCachedValue(clazz) {
    CachedValueProvider.Result.create(doFindSingleAbstractSignature(clazz), clazz)
  }
}

private fun doFindSingleAbstractSignature(clazz: PsiClass): HierarchicalMethodSignature? {
  var result: HierarchicalMethodSignature? = null
  for (signature in clazz.visibleSignatures) {
    if (!isEffectivelyAbstractMethod(signature)) continue
    if (result != null) return null // found another abstract method
    result = signature
  }
  return result
}

private fun isEffectivelyAbstractMethod(signature: HierarchicalMethodSignature): Boolean {
  val method = signature.method
  if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) return false
  if (isObjectMethod(signature)) return false
  if (isImplementedTraitMethod(method)) return false
  return true
}

private fun isObjectMethod(signature: HierarchicalMethodSignature): Boolean {
  return signature.superSignatures.any {
    it.method.containingClass?.qualifiedName == JAVA_LANG_OBJECT
  }
}

private fun isImplementedTraitMethod(method: PsiMethod): Boolean {
  val clazz = method.containingClass ?: return false
  if (!isTrait(clazz)) return false
  val traitMethod = method as? GrMethod ?: return false
  return traitMethod.block != null
}

fun isSamConversionAllowed(context: PsiElement): Boolean {
  return GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY2_2)
}
