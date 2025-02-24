// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.idea.base.facet.platform.platform as platformNew

@get:Deprecated("Only supported for Kotlin Plugin K1 mode. Use Kotlin Analysis API instead, which works for both K1 and K2 modes. See https://kotl.in/analysis-api and `org.jetbrains.kotlin.analysis.api.analyze` for details.")
@get:ApiStatus.ScheduledForRemoval
val KtElement.builtIns: KotlinBuiltIns get() = getResolutionFacade().moduleDescriptor.builtIns

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.platform.getPlatform' instead.",
    ReplaceWith("platform", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.platform.getPlatform' instead.",
    ReplaceWith("platform", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.platform: TargetPlatform
    get() = platformNew

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.platform.getPlatform' instead.",
    ReplaceWith("platform", "org.jetbrains.kotlin.idea.base.facet.platform.getPlatform.platform")
)
@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.platform.getPlatform' instead.",
    ReplaceWith("platform", "org.jetbrains.kotlin.idea.base.facet.platform.getPlatform.platform")
)
@Suppress("unused")
val KtElement.platform: TargetPlatform
    get() = platformNew
