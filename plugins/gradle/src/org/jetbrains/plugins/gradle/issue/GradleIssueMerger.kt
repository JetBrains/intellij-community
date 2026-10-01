// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssueMerger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@Internal
@OverrideOnly
interface GradleIssueMerger : BuildIssueMerger {
  companion object {
    @Internal
    val EP_NAME: ExtensionPointName<GradleIssueMerger> = ExtensionPointName.create("org.jetbrains.plugins.gradle.issueMerger")
  }
}
