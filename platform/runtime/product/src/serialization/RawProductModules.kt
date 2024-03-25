// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization

import com.intellij.platform.runtime.repository.RuntimeModuleId

class RawProductModules(
  val mainGroupModules: List<RawIncludedRuntimeModule>,
  val bundledPluginMainModules: List<RuntimeModuleId>,
  val includedFrom: List<RawIncludedFromData>,
)

class RawIncludedFromData(
  val fromModule: RuntimeModuleId,
  val withoutModules: Set<RuntimeModuleId>,
)