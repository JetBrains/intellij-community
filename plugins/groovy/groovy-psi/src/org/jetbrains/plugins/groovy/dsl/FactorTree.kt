// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder
import java.util.concurrent.ConcurrentHashMap

class FactorTree(
  project: Project,
  private val myExecutor: GroovyDslExecutor
) {

  private val myProvider: CachedValueProvider<MutableMap<Any, Any>> = CachedValueProvider {
    CachedValueProvider.Result(ConcurrentHashMap(), PsiModificationTracker.MODIFICATION_COUNT)
  }
  private val myTopLevelCache: CachedValue<MutableMap<Any, Any>> =
    CachedValuesManager.getManager(project).createCachedValue(myProvider, false)

  fun cache(descriptor: GroovyClassDescriptor, holder: CustomMembersHolder) {
    var current: MutableMap<Any, Any>? = null
    for (factor: Factor in descriptor.affectingFactors) {
      val key: Any = when (factor) {
        Factor.placeElement -> descriptor.place
        Factor.placeFile -> descriptor.placeFile
        Factor.qualifierType -> descriptor.psiType.getCanonicalText(false)
      }
      if (current == null) {
        if (key is UserDataHolder) {
          current = CachedValuesManager.getManager(descriptor.project).getCachedValue(key, GDSL_MEMBER_CACHE, myProvider, false)
          continue
        }
        current = myTopLevelCache.value!!
      }
      @Suppress("UNCHECKED_CAST")
      var next: MutableMap<Any, Any>? = current[key] as MutableMap<Any, Any>?
      if (next == null) {
        next = ConcurrentHashMap<Any, Any>()
        current[key] = next
        if (key is String) { // type
          current[CONTAINS_TYPE] = true
        }
      }
      current = next
    }
    if (current == null) {
      current = myTopLevelCache.value!!
    }
    current[myExecutor] = holder
  }

  fun retrieve(place: PsiElement, placeFile: PsiFile, qualifierType: NotNullLazyValue<String>): CustomMembersHolder? {
    return retrieveImpl(place, placeFile, qualifierType, myTopLevelCache.value, true)
  }

  private fun retrieveImpl(
    place: PsiElement,
    placeFile: PsiFile,
    qualifierType: NotNullLazyValue<String>,
    current: Map<*, *>?,
    topLevel: Boolean
  ): CustomMembersHolder? {
    if (current == null) return null
    val result: CustomMembersHolder? = current[myExecutor] as CustomMembersHolder?
    if (result != null) {
      return result
    }
    if (current.containsKey(CONTAINS_TYPE)) {
      retrieveImpl(place, placeFile, qualifierType, current[qualifierType.value] as Map<*, *>?, false)?.let {
        return it
      }
    }
    return retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(placeFile, current, topLevel), false)
           ?: retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(place, current, topLevel), false)
  }

  companion object {

    private val GDSL_MEMBER_CACHE: Key<CachedValue<MutableMap<Any, Any>>> = Key.create("GDSL_MEMBER_CACHE")

    private val CONTAINS_TYPE = Key.create<Boolean>("CONTAINS_TYPE")

    private fun getFromMapOrUserData(holder: UserDataHolder, map: Map<*, *>, fromUserData: Boolean): Map<*, *>? {
      if (fromUserData) {
        val cache = holder.getUserData(GDSL_MEMBER_CACHE)
        return if (cache != null && cache.hasUpToDateValue()) cache.value else null
      }
      return map[holder] as Map<*, *>?
    }
  }
}
