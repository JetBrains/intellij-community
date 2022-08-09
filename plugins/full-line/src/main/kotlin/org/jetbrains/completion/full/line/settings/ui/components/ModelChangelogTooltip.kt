package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.ide.HelpTooltip
import com.intellij.ui.components.labels.LinkLabel
import org.apache.commons.io.FileUtils


class ModelChangelogTooltip(action: () -> Unit) : LinkLabel<Any>("", null) {
    private val tooltip: HelpTooltip = HelpTooltip()
        .setLink("Download", action)
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

    fun attachChangelog(changelog: String, version: String, size: Long) {
        tooltip.setDescription(changelog)
            .setTitle("Changelog for $version")
            .installOn(this)
        text = "New version available (${FileUtils.byteCountToDisplaySize(size)})"
        isVisible = true
    }
}
