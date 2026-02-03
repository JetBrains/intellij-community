// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.openapi.util.BuildNumber
import org.jetbrains.idea.devkit.DevKitBundle

/**
 * Holds compatibility range of a plugin, consisting of [[sinceBuild]; [untilBuild]] build numbers.
 */
data class SinceUntilRange(val sinceBuild: BuildNumber?, val untilBuild: BuildNumber?) {

  fun asString() = when {
    sinceBuild != null && untilBuild != null -> sinceBuild.asString() + " - " + untilBuild.asString()
    sinceBuild != null -> sinceBuild.asString() + "+"
    untilBuild != null -> "1.0 - $untilBuild"
    else -> DevKitBundle.message("inspections.missing.recent.api.since.until.range.all.builds")
  }

  override fun toString() = asString()

}
