// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Build system check for the BTA CRI. The BTA CRI applies to a project when any implementation returns `true`.
 */
@ApiStatus.Internal
interface BtaKotlinCompilerReferenceIndexApplicabilityProvider {
    /**
     * Returns `true` when the build system generates CRI artifacts for the [project].
     * Provider selection calls it on the UI thread, so it must be fast and free of side effects.
     */
    fun isApplicable(project: Project): Boolean

    companion object {
        internal val EP_NAME: ExtensionPointName<BtaKotlinCompilerReferenceIndexApplicabilityProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.btaKotlinCompilerReferenceIndexApplicabilityProvider")
    }
}
