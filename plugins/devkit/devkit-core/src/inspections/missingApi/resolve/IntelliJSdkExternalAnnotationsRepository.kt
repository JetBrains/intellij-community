// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

/**
 * Repository interface to download external annotations for IntelliJ SDKs.
 */
interface IntelliJSdkExternalAnnotationsRepository {
  /**
   * Downloads external annotations suitable for IDE.
   *
   * Returns annotations to be attached, or `null` if no suitable annotations are found.
   */
  suspend fun downloadExternalAnnotations(ideBuildNumber: BuildNumber): IntelliJSdkExternalAnnotations?

  @ApiStatus.Internal
  companion object {
    const val RELEASES_REPO_URL: String = "https://www.jetbrains.com/intellij-repository/releases/"
    const val SNAPSHOTS_REPO_URL: String = "https://www.jetbrains.com/intellij-repository/snapshots/"
  }
}
