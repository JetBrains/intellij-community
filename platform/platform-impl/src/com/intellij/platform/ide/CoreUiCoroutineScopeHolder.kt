// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Service(value = [Service.Level.APP, Service.Level.PROJECT])
class CoreUiCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)