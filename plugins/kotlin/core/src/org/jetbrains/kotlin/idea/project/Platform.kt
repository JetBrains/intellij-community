// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.project

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.psi.KtElement

@K1Deprecation
@get:Deprecated("Only supported for Kotlin Plugin K1 mode. Use Kotlin Analysis API instead, which works for both K1 and K2 modes. See https://kotl.in/analysis-api and `org.jetbrains.kotlin.analysis.api.analyze` for details.")
@get:ApiStatus.ScheduledForRemoval
val KtElement.builtIns: KotlinBuiltIns get() = getResolutionFacade().moduleDescriptor.builtIns

