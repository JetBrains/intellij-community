// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

@Internal
@Experimental
class ProjectFrameActionExclusionBean {
  @JvmField
  @Attribute("frameType")
  var frameType: String = ""

  @JvmField
  @Attribute("place")
  var place: String = ""

  @JvmField
  @Attribute("id")
  var actionId: String = ""
}

/**
 * Resolves action ids that must be excluded for a given project-frame type and UI place.
 */
@Service(Service.Level.APP)
@Internal
@Experimental
class ProjectFrameActionExclusionService {
  companion object {
    @VisibleForTesting
    val EP_NAME: ExtensionPointName<ProjectFrameActionExclusionBean> = ExtensionPointName("com.intellij.projectFrameActionExclusion")

    suspend fun getInstance(): ProjectFrameActionExclusionService = serviceAsync()

    @Deprecated("Use getInstance instead", ReplaceWith("getInstance()"))
    fun getInstanceSync(): ProjectFrameActionExclusionService = service()
  }

  fun getExcludedActionIds(frameType: String?, place: String): Set<String> {
    val normalizedFrameType = frameType.normalizeKey() ?: return emptySet()
    val normalizedPlace = place.normalizeKey() ?: return emptySet()

    val extensions = EP_NAME.extensionsIfPointIsRegistered
    if (extensions.isEmpty()) {
      return emptySet()
    }

    val result = LinkedHashSet<String>(extensions.size)
    for (extension in extensions) {
      if (extension.frameType.normalizeKey() != normalizedFrameType) {
        continue
      }
      if (extension.place.normalizeKey() != normalizedPlace) {
        continue
      }

      val actionId = extension.actionId.normalizeKey() ?: continue
      result.add(actionId)
    }
    return result
  }
}

private fun String?.normalizeKey(): String? {
  return this?.trim()?.takeIf { it.isNotEmpty() }
}
