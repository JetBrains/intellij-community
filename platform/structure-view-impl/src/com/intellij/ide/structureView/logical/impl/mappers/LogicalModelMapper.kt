// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl.mappers

import com.intellij.ide.structureView.logical.model.LogicalModelPresentationProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LogicalModelMapper {
   fun  type(): String
   fun  attributes(): HashMap<String, Any>
}

@ApiStatus.Internal
interface LogicalModelMapperProvider<T> {
   fun  type(t: T): String
   fun  attributes(t: T): HashMap<String, Any>

   companion object {
     fun <T> getInstance(model: T): LogicalModelMapperProvider<T>? {
      return LogicalModelPresentationProvider.getForObject(model) as? LogicalModelMapperProvider<T>
     }
   }
}