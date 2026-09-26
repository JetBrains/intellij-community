// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.settings

import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionDiscoverySource
import org.jetbrains.kotlin.idea.core.script.definitions.scriptTemplateMarkerPath
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/** The height of two lines of text, plus the insets of the summary renderer. */
internal const val DEFINITION_ROW_HEIGHT: Int = 44

/** The text of a row starts here. The manual loading lists repeat it, so the page holds one margin. */
internal const val DEFINITION_ROW_LEFT_INSET: Int = 10

internal data class ScriptDefinitionTableModel(
    val id: String,
    val name: @NlsSafe String,
    val pattern: @NlsSafe String,
    /** The regular expression the definition matches a file by, when it has one beyond the extension. */
    val filePattern: @NlsSafe String?,
    val source: ScriptDefinitionDiscoverySource?,
    val canBeSwitchedOff: Boolean,
    var isEnabled: Boolean,
)

/** How the IDE reached the definition: one of Plugin, Marker file or Settings. */
internal val ScriptDefinitionDiscoverySource?.originText: @NlsSafe String
    get() = when (this) {
        is ScriptDefinitionDiscoverySource.Provider -> KotlinBaseScriptingBundle.message("script.definitions.source.plugin")
        is ScriptDefinitionDiscoverySource.MarkerFile -> KotlinBaseScriptingBundle.message("script.definitions.source.marker")
        is ScriptDefinitionDiscoverySource.Manual -> KotlinBaseScriptingBundle.message("script.definitions.source.settings")
        null -> ""
    }

/**
 * Where that origin points: the plugin, or the JAR holding the marker file.
 *
 * A manual definition points nowhere, because the settings load every listed class from one merged
 * classpath. Naming an entry of it would be a guess, so the row shows the origin alone.
 */
internal val ScriptDefinitionDiscoverySource?.locationText: @NlsSafe String
    get() = when (this) {
        is ScriptDefinitionDiscoverySource.Provider -> pluginName.orEmpty()
        is ScriptDefinitionDiscoverySource.MarkerFile -> displayLocation ?: jarPath.orEmpty()
        is ScriptDefinitionDiscoverySource.Manual -> ""
        null -> ""
    }

/** One row of the tooltip table. [code] marks a value the reader compares character by character. */
internal data class TooltipRow(
    val label: @NlsSafe String,
    val value: @NlsSafe String,
    val code: Boolean = false,
)

/**
 * What the tooltip says about a definition.
 *
 * The row already carries the pattern, the origin and the location, so this answers the rest: how the
 * IDE reached the definition and which exact artifacts are involved.
 */
internal fun ScriptDefinitionDiscoverySource?.tooltipRows(
    definitionFqn: @NlsSafe String,
    filePattern: @NlsSafe String? = null,
): List<TooltipRow> {
    val source = this
    return buildList {
        // The row shows the extension. Only a definition that matches by more than that has a pattern.
        filePattern?.let { add(row("script.definitions.tooltip.file.pattern", it, code = true)) }
        when (source) {
            is ScriptDefinitionDiscoverySource.Provider -> {
                add(row("script.definitions.tooltip.definition.source", source.implementationFqn, code = true))
                source.pluginId?.let { add(row("script.definitions.tooltip.plugin.id", it, code = true)) }
            }

            is ScriptDefinitionDiscoverySource.MarkerFile -> {
                add(row("script.definitions.tooltip.definition.source", rootName ?: displayLocation ?: jarPath ?: "", code = true))
                add(row("script.definitions.tooltip.marker", scriptTemplateMarkerPath(definitionFqn), code = true))
            }

            is ScriptDefinitionDiscoverySource.Manual -> {
                add(
                    row(
                        "script.definitions.tooltip.definition.source",
                        KotlinBaseScriptingBundle.message("script.definitions.source.settings")
                    )
                )
            }

            null -> Unit
        }
        add(row("script.definitions.tooltip.definition.class", definitionFqn, code = true))
    }
}

private fun row(key: String, value: @NlsSafe String, code: Boolean = false): TooltipRow =
    TooltipRow(KotlinBaseScriptingBundle.message(key), value, code)

/** Beyond this the tooltip needs a width, so a long path wraps instead of stretching off screen. */
private const val TOOLTIP_WRAP_THRESHOLD: Int = 70

private const val TOOLTIP_WIDTH: Int = 560

/**
 * A two column table: bold labels on the left, values on the right.
 *
 * A path and a class name hold no spaces, so a long one needs a break opportunity of its own. Only a
 * value over [TOOLTIP_WRAP_THRESHOLD] gets one, because the break costs a character that the value
 * does not hold. A short value stays what it says it is.
 */
internal fun List<TooltipRow>.toTooltipHtml(): HtmlChunk {
    var table = HtmlChunk.tag("table").attr("cellspacing", "0").attr("cellpadding", "2")
    if (any { it.value.length > TOOLTIP_WRAP_THRESHOLD }) {
        table = table.attr("width", TOOLTIP_WIDTH)
    }

    return table.children(
        map { (label, value, code) ->
            val text = HtmlChunk.text(if (value.length > TOOLTIP_WRAP_THRESHOLD) value.withWrapOpportunities() else value)
            HtmlChunk.tag("tr").children(
                HtmlChunk.tag("td").attr("valign", "top").children(HtmlChunk.tag("b").addText(label), HtmlChunk.nbsp(2)),
                HtmlChunk.tag("td").child(if (code) HtmlChunk.tag("code").child(text) else text),
            )
        }
    )
}

/**
 * A zero-width space after every separator, so the renderer can break the value.
 *
 * The space is invisible but real, so the result is no longer the value itself. Only a value too long
 * to fit is worth that, and a regular expression pays the most: the break splits `\.` in two.
 */
private fun String.withWrapOpportunities(): @NlsSafe String =
    replace("/", "/\u200b").replace("\\", "\\\u200b").replace(".", ".\u200b")

/** A row reads out its visible line first, then the provenance behind it. */
@NlsSafe
private fun accessibleDescription(detail: String, rows: List<TooltipRow>): String =
    (listOf(detail) + rows.map { "${it.label} ${it.value}" }).joinToString(". ")

/** The second line of a row: what the definition matches, how it was reached, and from where. */
internal val ScriptDefinitionTableModel.detailText: @NlsSafe String
    get() = listOf(pattern, source.originText, source.locationText).filter { it.isNotEmpty() }.joinToString(" \u00b7 ")

internal class ScriptDefinitionTable(definitions: MutableList<ScriptDefinitionTableModel>) : ListTableModel<ScriptDefinitionTableModel>(
    arrayOf(
        ScriptDefinitionSummary(),
        ScriptDefinitionIsEnabled(),
    ), definitions, 0
) {

    /** The name on the first line, the file pattern and the source on the second. */
    private class ScriptDefinitionSummary : ColumnInfo<ScriptDefinitionTableModel, ScriptDefinitionTableModel>(
        KotlinBaseScriptingBundle.message("script.definitions.column.name")
    ) {
        private val renderer = SummaryRenderer()

        override fun valueOf(item: ScriptDefinitionTableModel): ScriptDefinitionTableModel = item
        override fun getRenderer(item: ScriptDefinitionTableModel?): TableCellRenderer = renderer
    }

    /**
     * Two labels stacked in one cell. A label clips its own text and shows the full value in the
     * tooltip, which HTML in a cell cannot do.
     */
    private class SummaryRenderer : TableCellRenderer {
        private val nameLabel = JBLabel()
        private val detailLabel = JBLabel().apply { font = JBFont.small() }
        private val panel = JPanel(VerticalLayout(0)).apply {
            border = JBUI.Borders.empty(5, DEFINITION_ROW_LEFT_INSET)
            isOpaque = true
            add(nameLabel)
            add(detailLabel)
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val item = value as? ScriptDefinitionTableModel ?: return panel

            nameLabel.text = item.name
            detailLabel.text = item.detailText

            panel.background = if (isSelected) table.selectionBackground else table.background
            nameLabel.foreground = if (isSelected) table.selectionForeground else table.foreground
            detailLabel.foreground = if (isSelected) table.selectionForeground else UIUtil.getContextHelpForeground()

            // The row shows the friendly names, so the exact classes and files live here.
            val rows = item.source.tooltipRows(item.id, item.filePattern)
            panel.setToolTipText(rows.toTooltipHtml())

            panel.accessibleContext.accessibleName = item.name
            panel.accessibleContext.accessibleDescription = accessibleDescription(item.detailText, rows)
            return panel
        }
    }

    private class ScriptDefinitionIsEnabled : ColumnInfo<ScriptDefinitionTableModel, Boolean>(
        KotlinBaseScriptingBundle.message("script.definitions.column.enabled")
    ) {
        override fun getEditor(item: ScriptDefinitionTableModel?) = BooleanTableCellEditor()
        override fun getRenderer(item: ScriptDefinitionTableModel?) = BooleanTableCellRenderer()
        override fun getWidth(table: JTable?): Int = JBUI.scale(44)

        override fun valueOf(item: ScriptDefinitionTableModel): Boolean = item.isEnabled
        override fun setValue(item: ScriptDefinitionTableModel, value: Boolean) {
            item.isEnabled = value
        }

        override fun isCellEditable(item: ScriptDefinitionTableModel): Boolean = item.canBeSwitchedOff
    }
}
