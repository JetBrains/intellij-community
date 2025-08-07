// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId

class ProductModeMatcher(productMode: ProductMode) {
  private val myIncompatibleRootModule: String = ProductModes.getIncompatibleRootModule(productMode).stringId
  private val myCache: MutableMap<String, Boolean> = mutableMapOf()
  val unmatchedModules: MutableMap<RuntimeModuleId, List<RuntimeModuleId>> = mutableMapOf()

  fun matches(moduleDescriptor: RuntimeModuleDescriptor): Boolean {
    val stringId = moduleDescriptor.getModuleId().getStringId()
    val cached = myCache.get(stringId)
    if (cached != null) return cached

    if (myIncompatibleRootModule == stringId) {
      myCache[stringId] = false
      return false
    }

    myCache[stringId] = true //this is needed to prevent StackOverflowError in case of circular dependencies
    val nonMatchedModules = moduleDescriptor.getDependencies().filterNot { matches(it) }
    if (nonMatchedModules.isEmpty()) {
      return true
    }


    unmatchedModules[moduleDescriptor.getModuleId()] = nonMatchedModules.map { it.getModuleId() }
    myCache[stringId] = false
    return false
  }
}