// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId

class ProductModeMatcher(productMode: ProductMode) {
  private val myIncompatibleRootModule = ProductModeLoadingRules.getIncompatibleRootModules(productMode)
  private val myCache: MutableMap<RuntimeModuleId, Boolean> = mutableMapOf()
  val unmatchedModules: MutableMap<RuntimeModuleId, List<RuntimeModuleId>> = mutableMapOf()

  fun matches(moduleDescriptor: RuntimeModuleDescriptor): Boolean {
    val moduleId = moduleDescriptor.getModuleId()
    val cached = myCache.get(moduleId)
    if (cached != null) return cached

    if (myIncompatibleRootModule.contains(moduleId)) {
      myCache[moduleId] = false
      return false
    }

    myCache[moduleId] = true //this is needed to prevent StackOverflowError in case of circular dependencies
    val nonMatchedModules = moduleDescriptor.getDependencies().filterNot { matches(it) }
    if (nonMatchedModules.isEmpty()) {
      return true
    }


    unmatchedModules[moduleDescriptor.getModuleId()] = nonMatchedModules.map { it.getModuleId() }
    myCache[moduleId] = false
    return false
  }
}