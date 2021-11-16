// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.VcsCommitExternalStatus
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogCellController
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.VcsLogIconCellRenderer
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.VcsLogCustomColumn
import com.intellij.vcs.log.ui.table.column.util.VcsLogExternalStatusColumnService
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.table.TableCellRenderer

/**
 * Provides additional status of a commit in an external system (build status, metadata, etc.)
 */
@ApiStatus.Experimental
interface VcsCommitExternalStatusProvider<T : VcsCommitExternalStatus> {

  @get:NonNls
  val id: String

  /**
   * Must be disposed when the list of EPs is changed
   */
  @RequiresEdt
  fun createLoader(project: Project): VcsCommitsDataLoader<T>

  @RequiresEdt
  fun getPresentation(project: Project, status: T): VcsCommitExternalStatusPresentation?

  /**
   * Extend this class in order to display the status in a log table column
   */
  abstract class WithColumn<T : VcsCommitExternalStatus> : VcsCommitExternalStatusProvider<T> {

    val logColumn: VcsLogColumn<T> = ExternalStatusLogColumn()

    /**
     * Localized column name
     */
    @get:Nls
    protected abstract val columnName: String

    /**
     * Should the column be shown by default
     */
    protected abstract val isColumnEnabledByDefault: Boolean

    /**
     * Service that provides the column infrastructure
     * One should extend [com.intellij.vcs.log.ui.table.column.util.VcsLogExternalStatusColumnService], make it a service and return here
     */
    @RequiresEdt
    protected abstract fun getExternalStatusColumnService(): VcsLogExternalStatusColumnService<T>

    /**
     * Value that will be passed to a renderer when the table cell is empty or when an exception happens when calculating the status
     */
    @RequiresEdt
    protected abstract fun getStubStatus(): T

    private inner class ExternalStatusLogColumn : VcsLogCustomColumn<T> {
      override val id
        get() = this@WithColumn.id

      override val localizedName
        get() = columnName

      override val isDynamic = true
      override val isResizable = false

      override fun isEnabledByDefault() = isColumnEnabledByDefault

      override fun getStubValue(model: GraphTableModel) = getStubStatus()

      override fun getValue(model: GraphTableModel, row: Int) =
        getExternalStatusColumnService().getStatus(model, row) ?: getStubValue(model)

      override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
        getExternalStatusColumnService().initialize(table, this)
        return ExternalStatusCellRenderer(table)
      }
    }

    private inner class ExternalStatusCellRenderer(private val table: VcsLogGraphTable) : VcsLogIconCellRenderer() {

      private fun getStatusPresentation(row: Int) = table.model.getValueAt(row, logColumn).let {
        getPresentation(table.model.logData.project, it)
      }

      override fun customize(table: VcsLogGraphTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        //avoid unchecked cast by getting a value from the table model
        val presentation = getStatusPresentation(row) ?: return
        icon = presentation.icon
        toolTipText = presentation.text
      }

      override fun getCellController() = ClickController()

      private inner class ClickController : VcsLogCellController {
        //todo hand cursor works initially but then stops
        override fun performMouseMove(row: Int, e: MouseEvent): Cursor? {
          val presentation = getStatusPresentation(row)
          return if (presentation is VcsCommitExternalStatusPresentation.Clickable && presentation.clickEnabled(e))
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          else null
        }

        override fun performMouseClick(row: Int, e: MouseEvent): Cursor? {
          val presentation = getStatusPresentation(row)
          if (presentation is VcsCommitExternalStatusPresentation.Clickable && presentation.clickEnabled(e)) presentation.onClick(e)
          return null
        }
      }
    }
  }

  companion object {

    internal val EP = ExtensionPointName<VcsCommitExternalStatusProvider<*>>("com.intellij.vcsLogCommitStatusProvider")

    @JvmStatic
    fun getExtensionsWithColumns(): List<WithColumn<*>> = EP.extensions.filterIsInstance(WithColumn::class.java)

    @RequiresEdt
    @JvmStatic
    fun addProviderListChangeListener(disposable: Disposable, listener: () -> Unit) {
      EP.addChangeListener(listener, disposable)
    }
  }
}