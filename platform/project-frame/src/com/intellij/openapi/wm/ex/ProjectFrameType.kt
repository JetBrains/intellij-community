// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

/**
 * An action excluded from a UI [place] for the declaring project-frame type.
 */
@Internal
@Experimental
@Tag("excludeAction")
class ProjectFrameActionExclusionBean {
  @JvmField
  @Attribute
  @RequiredElement
  var place: String = ""

  @JvmField
  @Attribute("id")
  @RequiredElement
  var actionId: String = ""
}

/**
 * Declarative description of a project-frame type, keyed by [id] — the value carried by
 * `OpenProjectTask.projectFrameTypeId` and `RecentProjectMetaInfo.projectFrameTypeId`.
 *
 * This is the single place where per-frame-type policy is declared. Everything here is static: it must be
 * resolvable from the frame type id alone, because the earliest consumer (startup project reopening) runs
 * before any `Project` exists. Policy that needs a `Project` belongs to [ProjectFrameCapabilitiesProvider].
 */
@Internal
@Experimental
class ProjectFrameTypeBean {
  @JvmField
  @Attribute
  @RequiredElement
  var id: String = ""

  /**
   * Lets a hidden recent project of this frame type be reopened on IDE start.
   *
   * Hidden projects stay excluded from the Recent Projects list either way.
   */
  @JvmField
  @Attribute
  var reopenWhenHidden: Boolean = false

  /**
   * Id of the `com.intellij.projectFrameToolWindowLayout` profile seeded and applied for this frame type.
   */
  @JvmField
  @Attribute
  var toolWindowLayoutProfile: String? = null

  @Property(surroundWithTag = false)
  @XCollection(elementName = "excludeAction")
  var excludedActions: List<ProjectFrameActionExclusionBean> = emptyList()
}

/**
 * Resolves static policy declared for a project-frame type.
 *
 * Also acts as the registry of known frame type ids: [findDescriptor] returns `null` for an id nothing declares,
 * which is how callers can tell "no policy" apart from "typo or missing plugin".
 */
@Service(Service.Level.APP)
@Internal
@Experimental
class ProjectFrameTypeService {
  companion object {
    @VisibleForTesting
    val EP_NAME: ExtensionPointName<ProjectFrameTypeBean> = ExtensionPointName("com.intellij.projectFrameType")
  }

  /**
   * Returns the descriptor declared for [frameTypeId], or `null` when the id is blank or undeclared.
   *
   * If several descriptors declare the same id, the first one is kept and an error is logged.
   */
  fun findDescriptor(frameTypeId: String?): ProjectFrameTypeBean? {
    val normalizedId = frameTypeId.normalizeProjectFrameKey() ?: return null

    var result: ProjectFrameTypeBean? = null
    for (bean in EP_NAME.extensionsIfPointIsRegistered) {
      if (bean.id.normalizeProjectFrameKey() != normalizedId) {
        continue
      }

      if (result == null) {
        result = bean
      }
      else {
        LOG.error("Multiple project frame types are declared for id '$normalizedId'. Keeping the first one.")
      }
    }
    return result
  }

  fun canReopenWhenHidden(frameTypeId: String?): Boolean = findDescriptor(frameTypeId)?.reopenWhenHidden == true

  fun getToolWindowLayoutProfileId(frameTypeId: String?): String? {
    return findDescriptor(frameTypeId)?.toolWindowLayoutProfile.normalizeProjectFrameKey()
  }

  fun getExcludedActionIds(frameTypeId: String?, place: String): Set<String> {
    val normalizedPlace = place.normalizeProjectFrameKey() ?: return emptySet()
    val excludedActions = findDescriptor(frameTypeId)?.excludedActions ?: return emptySet()
    if (excludedActions.isEmpty()) {
      return emptySet()
    }

    val result = LinkedHashSet<String>(excludedActions.size)
    for (exclusion in excludedActions) {
      if (exclusion.place.normalizeProjectFrameKey() != normalizedPlace) {
        continue
      }

      val actionId = exclusion.actionId.normalizeProjectFrameKey() ?: continue
      result.add(actionId)
    }
    return result
  }
}

private val LOG = logger<ProjectFrameTypeService>()
