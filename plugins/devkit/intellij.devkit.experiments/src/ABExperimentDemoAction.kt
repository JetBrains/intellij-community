// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.experiments

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.experiment.ab.impl.ABExperimentDecision
import com.intellij.platform.experiment.ab.impl.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.ABExperimentUserData
import com.intellij.platform.experiment.ab.impl.ExperimentAssignment
import com.intellij.platform.experiment.ab.impl.IntelliJPlatformProduct
import com.intellij.platform.experiment.ab.impl.NUMBER_OF_BUCKETS
import com.intellij.platform.experiment.ab.impl.experimentsPartition
import com.intellij.platform.experiment.ab.impl.getABExperimentUserData
import com.intellij.platform.experiment.ab.impl.getActualABExperimentUserData
import com.intellij.platform.experiment.ab.impl.getExperimentDecision
import com.intellij.platform.experiment.ab.impl.isAllowed
import com.intellij.platform.experiment.ab.impl.setABExperimentUserDataOverride
import com.intellij.ui.table.JBTable
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

internal class ABExperimentDemoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ABExperimentDemoDialog(e.project).show()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class ABExperimentDemoDialog(project: Project?) : DialogWrapper(project, true) {
  private val productComboBox = ComboBox(IntelliJPlatformProduct.entries.toTypedArray())
  private val versionField = JTextField()
  private val bucketSpinner = JSpinner(SpinnerNumberModel(0, 0, NUMBER_OF_BUCKETS - 1, 1))
  private val experimentTableModel = ExperimentTableModel()
  private val experimentTable = JBTable(experimentTableModel)
  private val actualUserData = getActualABExperimentUserData()
  private var appliedUserData = getABExperimentUserData()
  private lateinit var applyAction: Action
  private lateinit var resetAction: Action

  init {
    title = DevkitExperimentBundle.message("dialog.title")
    loadUserData()
    experimentTable.autoCreateRowSorter = true
    init()
    installUserDataListeners()
    updateActionState()
  }

  override fun createCenterPanel(): JComponent {
    return JPanel(BorderLayout(0, 8)).apply {
      add(createUserDataPanel(), BorderLayout.NORTH)
      add(JScrollPane(experimentTable), BorderLayout.CENTER)
      preferredSize = Dimension(1000, 500)
    }
  }

  override fun createActions(): Array<Action> {
    applyAction = object : DialogWrapperAction(DevkitExperimentBundle.message("button.apply")) {
      override fun doAction(e: ActionEvent?) {
        applyUserData()
      }
    }
    resetAction = object : AbstractAction(DevkitExperimentBundle.message("button.reset")) {
      override fun actionPerformed(e: ActionEvent?) {
        setABExperimentUserDataOverride(null)
        appliedUserData = actualUserData
        loadUserData()
        refreshExperimentTable()
        setErrorText(null)
        updateActionState()
      }
    }
    return arrayOf(applyAction, resetAction, cancelAction)
  }

  private fun createUserDataPanel(): JPanel = JPanel(GridBagLayout()).apply {
    addField(0, DevkitExperimentBundle.message("user.product"), productComboBox)
    addField(1, DevkitExperimentBundle.message("user.version"), versionField)
    addField(2, DevkitExperimentBundle.message("user.bucket"), bucketSpinner)
  }

  private fun JPanel.addField(row: Int, label: @Nls String, component: JComponent) {
    val labelComponent = JLabel(label).apply {
      labelFor = component
    }
    add(labelComponent, GridBagConstraints().apply {
      gridx = 0
      gridy = row
      anchor = GridBagConstraints.WEST
      insets = Insets(0, 0, 4, 8)
    })
    add(component, GridBagConstraints().apply {
      gridx = 1
      gridy = row
      weightx = 1.0
      fill = GridBagConstraints.HORIZONTAL
      insets = Insets(0, 0, 4, 0)
    })
  }

  private fun applyUserData() {
    val userData = getEditedUserData()
    if (userData.fullVersion.isEmpty()) {
      setErrorText(DevkitExperimentBundle.message("error.empty.version"), versionField)
      return
    }

    setABExperimentUserDataOverride(userData.takeIf { it != actualUserData })
    appliedUserData = userData
    refreshExperimentTable()
    setErrorText(null)
    updateActionState()
  }

  private fun loadUserData() {
    productComboBox.selectedItem = appliedUserData.product
    versionField.text = appliedUserData.fullVersion
    bucketSpinner.value = appliedUserData.bucketNumber
  }

  private fun installUserDataListeners() {
    productComboBox.addActionListener { updateActionState() }
    versionField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) = updateActionState()

      override fun removeUpdate(e: DocumentEvent) = updateActionState()

      override fun changedUpdate(e: DocumentEvent) = updateActionState()
    })
    bucketSpinner.addChangeListener { updateActionState() }
  }

  private fun updateActionState() {
    val currentUserData = getEditedUserData()
    applyAction.isEnabled = currentUserData != appliedUserData
    resetAction.isEnabled = appliedUserData != actualUserData
  }

  private fun getEditedUserData(): ABExperimentUserData = ABExperimentUserData(
    product = productComboBox.selectedItem as IntelliJPlatformProduct,
    fullVersion = versionField.text.trim(),
    bucketNumber = bucketSpinner.value as Int,
  )

  private fun refreshExperimentTable() {
    experimentTableModel.userData = appliedUserData
  }
}

private class ExperimentTableModel : AbstractTableModel() {
  private val rows = ABExperimentOption.entries
    .filter { it != ABExperimentOption.UNASSIGNED }
    .flatMap { option ->
      val assignments = experimentsPartition.filter { it.experiment == option }
      if (assignments.isEmpty()) listOf(ExperimentRow(option, null)) else assignments.map { ExperimentRow(option, it) }
    }

  var userData: ABExperimentUserData = getABExperimentUserData()
    set(value) {
      field = value
      fireTableDataChanged()
    }

  override fun getRowCount(): Int = rows.size

  override fun getColumnCount(): Int = COLUMN_KEYS.size

  override fun getColumnName(column: Int): String = DevkitExperimentBundle.message(COLUMN_KEYS[column])

  override fun getValueAt(rowIndex: Int, columnIndex: Int): String {
    val row = rows[rowIndex]
    return when (columnIndex) {
      0 -> row.option.name
      1 -> row.assignment?.experimentBuckets?.formatRanges().orEmpty()
      2 -> row.assignment?.controlBuckets?.formatRanges().orEmpty()
      3 -> row.assignment?.majorVersion ?: DevkitExperimentBundle.message("experiment.any.version")
      4 -> row.assignment?.products?.joinToString() ?: DevkitExperimentBundle.message("experiment.no.assignment")
      5 -> row.state(getExperimentDecision(userData))
      else -> error("Unexpected column: $columnIndex")
    }
  }

  private fun ExperimentRow.state(decision: ABExperimentDecision): String {
    val assignment = assignment ?: return DevkitExperimentBundle.message("experiment.no.assignment")
    if (decision.option != option ||
        (decision.bucketNumber !in assignment.experimentBuckets && decision.bucketNumber !in assignment.controlBuckets)) {
      return DevkitExperimentBundle.message("experiment.not.selected")
    }
    if (decision.isControlGroup) return DevkitExperimentBundle.message("experiment.control")
    return if (isAllowed(option)) DevkitExperimentBundle.message("experiment.enabled") else DevkitExperimentBundle.message("experiment.not.allowed")
  }

  private fun Set<Int>.formatRanges(): String {
    if (isEmpty()) return ""
    val sortedBuckets = sorted()
    return buildList {
      var rangeStart = sortedBuckets.first()
      var rangeEnd = rangeStart
      for (bucket in sortedBuckets.drop(1)) {
        if (bucket == rangeEnd + 1) {
          rangeEnd = bucket
        }
        else {
          add(rangeStart.formatRangeEnd(rangeEnd))
          rangeStart = bucket
          rangeEnd = bucket
        }
      }
      add(rangeStart.formatRangeEnd(rangeEnd))
    }.joinToString()
  }

  private fun Int.formatRangeEnd(end: Int): String = if (this == end) toString() else "$this-$end"

  private data class ExperimentRow(val option: ABExperimentOption, val assignment: ExperimentAssignment?)

  private companion object {
    private val COLUMN_KEYS = arrayOf(
      "table.experiment",
      "table.experiment.buckets",
      "table.control.buckets",
      "table.version",
      "table.products",
      "table.state",
    )
  }
}
