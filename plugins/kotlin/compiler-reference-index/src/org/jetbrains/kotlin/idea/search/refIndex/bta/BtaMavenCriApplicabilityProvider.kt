// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface BtaMavenCriApplicabilityProvider {
    fun isApplicable(project: Project): Boolean

    companion object {
        val EP_NAME: ExtensionPointName<BtaMavenCriApplicabilityProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.btaMavenCriApplicabilityProvider")
    }
}
