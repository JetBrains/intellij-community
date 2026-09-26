// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level coroutine scope for the scope model. Callers that outlive a single request take a
 * [com.intellij.platform.util.coroutines.childScope] of it and cancel that instead.
 */
@Service(Service.Level.PROJECT)
internal class ScopesCoroutineScopeHolder(val coroutineScope: CoroutineScope)
