// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * A service that can be injected to other services to provide a [CoroutineScope].
 */
@Service(Service.Level.PROJECT)
internal class GradleProgressCoroutineScopeProvider(val cs: CoroutineScope)