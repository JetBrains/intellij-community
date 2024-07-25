// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.idea.base.facet.platform.platform as platformNew

val KtElement.builtIns: KotlinBuiltIns get() = getResolutionFacade().moduleDescriptor.builtIns

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.platform.getPlatform' instead.",
    ReplaceWith("platform", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.platform: TargetPlatform
    get() = platformNew

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.platform.getPlatform' instead.",
    ReplaceWith("platform", "org.jetbrains.kotlin.idea.base.facet.platform.getPlatform.platform")
)
@Suppress("unused")
val KtElement.platform: TargetPlatform
    get() = platformNew
