// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiClass
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

  private val myTopLevelCache: CachedValue<MutableMap<Any, Any>> =
    CachedValuesManager.getManager(project).createCachedValue(ourProvider, false)

  fun cache(descriptor: GroovyClassDescriptor, holder: CustomMembersHolder) {
    val map: MutableMap<Any, Any> = findOrCreateMap(descriptor)
    map[myExecutor] = holder
  }

  /**
   * Depending on affecting factors the method follows one of the paths:
   * - top level cache
   * - place
   * - file
   * - qualifierType
   * - place -> qualifierType
   * - file -> qualifierType
   *
   * There is not much sense in following "place -> file -> type" path,
   * because place as an affecting factor implicitly implies file as an affecting factor.
   */
  private fun findOrCreateMap(descriptor: GroovyClassDescriptor): MutableMap<Any, Any> {
    val affectingFactors = descriptor.affectingFactors
    if (affectingFactors.isEmpty()) {
      return myTopLevelCache.value
    }
    val userDataHolder: UserDataHolder = when {
      Factor.placeElement in affectingFactors -> descriptor.place
      Factor.placeFile in affectingFactors -> descriptor.placeFile
      Factor.qualifierType in affectingFactors -> descriptor.psiClass
      else -> error("can't happen")
    }
    val userDataMap = CachedValuesManager.getManager(descriptor.project).getCachedValue(
      userDataHolder, GDSL_MEMBER_CACHE, ourProvider, false
    )
    if (Factor.qualifierType in affectingFactors && (Factor.placeElement in affectingFactors || Factor.placeFile in affectingFactors)) {
      @Suppress("UNCHECKED_CAST")
      return userDataMap.getOrPut(descriptor.psiClass) {
        ConcurrentHashMap<Any, Any>(1)
      } as MutableMap<Any, Any>
    }
    else {
      return userDataMap
    }
  }

  fun retrieve(descriptor: GroovyClassDescriptor): CustomMembersHolder? {
    return myTopLevelCache.value.byExecutor()
           ?: byUserDataOrInnerMap(descriptor.justGetPlace(), descriptor.justGetPsiClass())
           ?: byUserDataOrInnerMap(descriptor.justGetPlaceFile(), descriptor.justGetPsiClass())
           ?: fromUserData(descriptor.justGetPsiClass())?.byExecutor()
  }

  private fun byUserDataOrInnerMap(holder: UserDataHolder, psiClass: PsiClass): CustomMembersHolder? {
    val map = fromUserData(holder) ?: return null
    return map.byExecutor()
           ?: fromMap(map, psiClass)?.byExecutor()
  }

  private fun Map<*, *>.byExecutor(): CustomMembersHolder? {
    return this[myExecutor] as CustomMembersHolder?
  }

  companion object {

    private val ourProvider: CachedValueProvider<MutableMap<Any, Any>> = CachedValueProvider {
      CachedValueProvider.Result(ConcurrentHashMap(1), PsiModificationTracker.MODIFICATION_COUNT)
    }

    private val GDSL_MEMBER_CACHE: Key<CachedValue<MutableMap<Any, Any>>> = Key.create("GDSL_MEMBER_CACHE")

    private fun fromUserData(holder: UserDataHolder): Map<*, *>? {
      return holder.getUserData(GDSL_MEMBER_CACHE)?.takeIf { it.hasUpToDateValue() }?.value
    }

    private fun fromMap(map: Map<*, *>, key: Any): Map<*, *>? {
      return map[key] as Map<*, *>?
    }
  }
}
