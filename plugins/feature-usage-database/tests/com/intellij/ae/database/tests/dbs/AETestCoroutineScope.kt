// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.tests.dbs

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope

@Service
class AETestCoroutineScope(private val cs: CoroutineScope) {
  companion object {
    fun getScope() = service<AETestCoroutineScope>().cs
  }
}