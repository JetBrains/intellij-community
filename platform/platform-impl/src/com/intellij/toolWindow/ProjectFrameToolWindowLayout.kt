// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

@Internal
class ProjectFrameToolWindowLayoutBean {
  @JvmField
  @Attribute
  @RequiredElement
  var id: String = ""

  @JvmField
  @Attribute("frameType")
  @RequiredElement
  var frameType: String = ""

  @JvmField
  @Attribute("applyMode", converter = ToolWindowLayoutApplyModeConverter::class)
  var applyMode: ToolWindowLayoutApplyMode = ToolWindowLayoutApplyMode.SEED_ONLY

  @JvmField
  @Attribute("migrationVersion")
  var migrationVersion: Int = 0

  @Property(surroundWithTag = false)
  @XCollection(elementName = "toolWindow")
  var toolWindows: List<ProjectFrameToolWindowBean> = emptyList()
}

@Internal
@Tag("toolWindow")
class ProjectFrameToolWindowBean {
  @JvmField
  @Attribute
  @RequiredElement
  var id: String = ""

  @JvmField
  @Attribute
  var register: Boolean = true

  @JvmField
  @Attribute
  var anchor: String? = null

  @JvmField
  @Attribute
  var visible: Boolean? = null

  @JvmField
  @Attribute("showStripeButton")
  var showStripeButton: Boolean? = null

  @JvmField
  @Attribute
  var weight: Float? = null

  @JvmField
  @Attribute("contentUiType")
  var contentUiType: String? = null

  @JvmField
  @Attribute
  var split: Boolean? = null

  @JvmField
  @Attribute
  var sideWeight: Float? = null
}

@Internal
class ToolWindowLayoutApplyModeConverter : Converter<ToolWindowLayoutApplyMode>() {
  override fun fromString(value: String): ToolWindowLayoutApplyMode? {
    return when (value.trim()) {
      "seedOnly", "SEED_ONLY" -> ToolWindowLayoutApplyMode.SEED_ONLY
      "forceOnce", "FORCE_ONCE" -> ToolWindowLayoutApplyMode.FORCE_ONCE
      else -> null
    }
  }

  override fun toString(value: ToolWindowLayoutApplyMode): String {
    return when (value) {
      ToolWindowLayoutApplyMode.SEED_ONLY -> "seedOnly"
      ToolWindowLayoutApplyMode.FORCE_ONCE -> "forceOnce"
    }
  }
}

@Service(Service.Level.APP)
@Internal
class ProjectFrameToolWindowLayoutService {
  companion object {
    @VisibleForTesting
    val EP_NAME: ExtensionPointName<ProjectFrameToolWindowLayoutBean> = ExtensionPointName("com.intellij.projectFrameToolWindowLayout")
  }

  @Suppress("UNUSED_PARAMETER")
  fun getProfile(project: Project, profileId: String, isNewUi: Boolean): ToolWindowLayoutProfile? {
    if (project.isDisposed) {
      return null
    }

    val bean = findProfileBean(profileId) ?: return null
    return ToolWindowLayoutProfile(
      layout = createLayout(bean),
      applyMode = bean.applyMode,
      migrationVersion = bean.migrationVersion.coerceAtLeast(0),
    )
  }

  fun isToolWindowRegistrationSuppressed(frameType: String?, toolWindowId: String): Boolean {
    return isToolWindowRegistrationSuppressed(frameType = frameType, profileId = null, toolWindowId = toolWindowId)
  }

  fun isToolWindowRegistrationSuppressed(frameType: String?, profileId: String?, toolWindowId: String): Boolean {
    val normalizedToolWindowId = toolWindowId.normalizeKey() ?: return false
    return getSuppressedToolWindowIds(frameType = frameType, profileId = profileId).contains(normalizedToolWindowId)
  }

  fun getSuppressedToolWindowIds(frameType: String?): Set<String> {
    return getSuppressedToolWindowIds(frameType = frameType, profileId = null)
  }

  fun getSuppressedToolWindowIds(frameType: String?, profileId: String?): Set<String> {
    val normalizedFrameType = frameType.normalizeKey()
    val normalizedProfileId = profileId.normalizeKey()
    if (normalizedFrameType == null && normalizedProfileId == null) {
      return emptySet()
    }

    val result = LinkedHashSet<String>()
    for (bean in EP_NAME.extensionList) {
      if (bean.frameType.normalizeKey() != normalizedFrameType && bean.id.normalizeKey() != normalizedProfileId) {
        continue
      }
      for (toolWindow in bean.toolWindows) {
        if (!toolWindow.register) {
          toolWindow.id.normalizeKey()?.let(result::add)
        }
      }
    }
    return result
  }

  private fun findProfileBean(profileId: String): ProjectFrameToolWindowLayoutBean? {
    val normalizedProfileId = profileId.normalizeKey() ?: return null

    var result: ProjectFrameToolWindowLayoutBean? = null
    for (bean in EP_NAME.extensionList) {
      if (bean.id.normalizeKey() != normalizedProfileId) {
        continue
      }

      if (result == null) {
        result = bean
      }
      else {
        LOG.error("Multiple project frame tool window layouts are provided for profile '$normalizedProfileId'. Keeping the first one.")
      }
    }
    return result
  }

  private fun createLayout(bean: ProjectFrameToolWindowLayoutBean): DesktopLayout {
    val baseLayout = ToolWindowDefaultLayoutManager.getInstance().getLayoutCopy()
    val infos = baseLayout.getInfos()
      .asSequence()
      .associateTo(LinkedHashMap()) { (id, info) -> id to info.copy() }

    for (toolWindow in bean.toolWindows) {
      val id = toolWindow.id.normalizeKey() ?: continue
      if (!toolWindow.register) {
        infos.remove(id)
        continue
      }

      if (!toolWindow.hasLayoutAttributes()) {
        continue
      }

      val info = infos[id] ?: WindowInfoImpl()
      val anchor = toolWindow.anchor.normalizeKey()?.let(ToolWindowAnchor::fromText)
      val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
      val needsOrder = info.id == null || info.toolWindowPaneId != paneId || (anchor != null && info.anchor != anchor) || info.order < 0

      info.id = id
      info.toolWindowPaneId = paneId
      if (anchor != null) {
        info.anchor = anchor
      }
      if (needsOrder) {
        info.order = getNextOrder(infos = infos, anchor = info.anchor)
      }

      toolWindow.visible?.let { info.isVisible = it }
      toolWindow.showStripeButton?.let { info.isShowStripeButton = it }
      toolWindow.weight?.let { info.weight = it }
      toolWindow.contentUiType.normalizeKey()?.let { info.contentUiType = ToolWindowContentUiType.getInstance(it) }
      toolWindow.split?.let { info.isSplit = it }
      toolWindow.sideWeight?.let { info.sideWeight = it }
      infos[id] = info
    }

    return DesktopLayout(infos, baseLayout.unifiedWeights.copy())
  }
}

private fun ProjectFrameToolWindowBean.hasLayoutAttributes(): Boolean {
  return anchor.normalizeKey() != null ||
         visible != null ||
         showStripeButton != null ||
         weight != null ||
         contentUiType.normalizeKey() != null ||
         split != null ||
         sideWeight != null
}

private fun getNextOrder(infos: Map<String, WindowInfoImpl>, anchor: ToolWindowAnchor): Int {
  return infos.values.asSequence()
           .filter { it.toolWindowPaneId == WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID && it.anchor == anchor && it.order >= 0 }
           .maxOfOrNull { it.order + 1 } ?: 0
}

private fun String?.normalizeKey(): String? {
  return this?.trim()?.takeIf { it.isNotEmpty() }
}

private val LOG = logger<ProjectFrameToolWindowLayoutService>()
