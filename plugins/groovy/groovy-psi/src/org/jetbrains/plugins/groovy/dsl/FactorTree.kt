// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
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
    CachedValueProvider.Result(ConcurrentHashMap(1), PsiModificationTracker.MODIFICATION_COUNT)
  }
  private val myTopLevelCache: CachedValue<MutableMap<Any, Any>> =
    CachedValuesManager.getManager(project).createCachedValue(myProvider, false)

  fun cache(descriptor: GroovyClassDescriptor, holder: CustomMembersHolder) {
    var current: MutableMap<Any, Any>? = null
    for (factor: Factor in descriptor.affectingFactors) {
      val key: UserDataHolder = when (factor) {
        Factor.placeElement -> descriptor.place
        Factor.placeFile -> descriptor.placeFile
        Factor.qualifierType -> descriptor.psiClass
      }
      if (current == null) {
        current = CachedValuesManager.getManager(descriptor.project).getCachedValue(key, GDSL_MEMBER_CACHE, myProvider, false)
        continue
      }
      @Suppress("UNCHECKED_CAST")
      var next: MutableMap<Any, Any>? = current[key] as MutableMap<Any, Any>?
      if (next == null) {
        next = ConcurrentHashMap<Any, Any>(1)
        current[key] = next
      }
      current = next
    }
    if (current == null) {
      current = myTopLevelCache.value!!
    }
    current[myExecutor] = holder
  }

  fun retrieve(descriptor: GroovyClassDescriptor): CustomMembersHolder? {
    return retrieveImpl(descriptor, myTopLevelCache.value, true)
  }

  private fun retrieveImpl(
    descriptor: GroovyClassDescriptor,
    current: Map<*, *>?,
    topLevel: Boolean
  ): CustomMembersHolder? {
    if (current == null) return null
    return current[myExecutor] as CustomMembersHolder?
           ?: retrieveImpl(descriptor, getFromMapOrUserData(descriptor.justGetPsiClass(), current, topLevel), false)
           ?: retrieveImpl(descriptor, getFromMapOrUserData(descriptor.justGetPlaceFile(), current, topLevel), false)
           ?: retrieveImpl(descriptor, getFromMapOrUserData(descriptor.justGetPlace(), current, topLevel), false)
  }

  companion object {

    private val GDSL_MEMBER_CACHE: Key<CachedValue<MutableMap<Any, Any>>> = Key.create("GDSL_MEMBER_CACHE")

    private fun getFromMapOrUserData(holder: UserDataHolder, map: Map<*, *>, fromUserData: Boolean): Map<*, *>? {
      if (fromUserData) {
        val cache = holder.getUserData(GDSL_MEMBER_CACHE)
        return if (cache != null && cache.hasUpToDateValue()) cache.value else null
      }
      return map[holder] as Map<*, *>?
    }
  }
}
