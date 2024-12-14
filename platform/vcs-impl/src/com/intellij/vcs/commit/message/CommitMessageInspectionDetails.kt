// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeHighlighting.HighlightDisplayLevel.Companion.find
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.Descriptor
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.ScopeToolState
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.profile.codeInspection.ui.LevelChooserAction
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable
import com.intellij.ui.GuiUtils
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.JComponent

@ApiStatus.Internal
class CommitMessageInspectionDetails(
  private val myProject: Project,
  private val myProfile: InspectionProfileImpl,
  private val myDefaultDescriptor: Descriptor,
) : Disposable {
  private val myToolState: ScopeToolState
  private val mySeverityChooser: LevelChooserAction

  val component: DialogPanel

  private val myEventDispatcher = EventDispatcher.create<ChangeListener?>(ChangeListener::class.java)

  init {
    myToolState = myDefaultDescriptor.state

    mySeverityChooser = MySeverityChooser(myProfile.profileManager.getSeverityRegistrar())
    val severityPanel = mySeverityChooser.createCustomComponent(mySeverityChooser.getTemplatePresentation(), ActionPlaces.UNKNOWN)

    val tool = myToolState.tool.getTool() as? BaseCommitMessageInspection
    val disposable = this

    component = panel {
      row(InspectionsBundle.message("inspection.severity")) {
        cell(severityPanel) // ComboBoxButton
      }

      val createDefault: Boolean
      if (tool != null) {
        val panel = this@panel
        with(tool) {
          createDefault = panel.createOptions(myProject, disposable)
        }
      }
      else {
        createDefault = true
      }

      if (createDefault) {
        val defaultPanel = myToolState.getAdditionalConfigPanel(disposable, myProject)
        if (defaultPanel != null) {
          row {
            cell(defaultPanel)
          }
        }
      }
    }

    component.reset()
  }

  override fun dispose() {
  }

  val key: HighlightDisplayKey get() = myDefaultDescriptor.key

  fun update() {
    mySeverityChooser.setChosen(ScopesAndSeveritiesTable.getSeverity(mutableListOf<ScopeToolState?>(myToolState)))
    GuiUtils.enableChildren(myToolState.isEnabled, component)
  }

  fun addListener(listener: ChangeListener) {
    myEventDispatcher.addListener(listener)
  }

  private inner class MySeverityChooser(registrar: SeverityRegistrar) : LevelChooserAction(registrar) {
    override fun onChosen(severity: HighlightSeverity) {
      val level = find(severity)

      myProfile.setErrorLevel(myDefaultDescriptor.key, level!!, null, myProject)
      myEventDispatcher.getMulticaster().onSeverityChanged(severity)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return createComboBoxButton(presentation)
    }
  }

  interface ChangeListener : EventListener {
    fun onSeverityChanged(severity: HighlightSeverity)
  }
}
