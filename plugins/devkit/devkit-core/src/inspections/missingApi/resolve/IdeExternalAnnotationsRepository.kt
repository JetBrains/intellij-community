// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.openapi.util.BuildNumber

/**
 * Repository interface to download external annotations for IDEs.
 */
interface IdeExternalAnnotationsRepository {
  /**
   * Downloads external annotations suitable for IDE.
   *
   * Returns annotations to be attached, or `null` if no suitable annotations are found.
   */
  fun downloadExternalAnnotations(ideBuildNumber: BuildNumber): IdeExternalAnnotations?
}