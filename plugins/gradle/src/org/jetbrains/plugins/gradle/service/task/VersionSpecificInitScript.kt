// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.task

import org.gradle.util.GradleVersion

data class VersionSpecificInitScript(val script: String, val filePrefix: String? = null, val isApplicable: (GradleVersion) -> Boolean) {
  fun isApplicableTo(ver: GradleVersion) = isApplicable(ver)
}