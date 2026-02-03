// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File
import java.io.Serializable

interface KotlinTargetJar : Serializable {
    val archiveFile: File?
    /**
     * Compilations that are in dependencies of the target's jar task.
     * Usually it's just the main compilation, but target can potentially be configured to include outputs of multiple compilations.
     * Note: it's currently impossible (or at least not straightforward) to make such configurations work for non-JVM targets.
     */
    @Deprecated("This property is unsupported since Kotlin 2.1.20. Please don't use it.")
    val compilations: Collection<KotlinCompilation>
}
