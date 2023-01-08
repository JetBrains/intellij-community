package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.ui.components.labels.LinkLabel
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message

class ModelChangelogTooltip(action: () -> Unit) : LinkLabel<Any>("", null) {
  private val tooltip: HelpTooltip = HelpTooltip()
    .setLink(message("full.line.label.download"), action)
    .setNeverHideOnTimeout(true)
    .setLocation(HelpTooltip.Alignment.BOTTOM)

  init {
    isVisible = false
  }

  override fun addNotify() {
    super.addNotify()
    tooltip.installOn(this)
  }

  override fun removeNotify() {
    HelpTooltip.dispose(this)
    super.removeNotify()
  }

  fun attachChangelog(@NlsContexts.Tooltip changelog: String, version: String, size: Long) {
    tooltip.setDescription(changelog)
      .setTitle(message("full.line.tooltip.download.title", version))
      .installOn(this)
    text = message("full.line.tooltip.download.title", StringUtilRt.formatFileSize(size))
    isVisible = true
  }
}
