// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeHighlighting.HighlightDisplayLevel.Companion.find
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.ex.Descriptor
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.ScopeToolState
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.ui.LevelChooserAction
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable
import com.intellij.ui.GuiUtils
import com.intellij.util.EventDispatcher
import com.intellij.util.ObjectUtils
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.JComponent

@ApiStatus.Internal
class CommitMessageInspectionDetails(
  private val myProject: Project,
  private val myProfile: InspectionProfileImpl,
  private val myDefaultDescriptor: Descriptor
) : UnnamedConfigurable, Disposable {
  private val myToolState: ScopeToolState
  private val mySeverityChooser: LevelChooserAction
  private val myOptionsConfigurable: ConfigurableUi<Project?>?
  private val myMainPanel: CommitMessageInspectionDetailsPanel

  private val myEventDispatcher = EventDispatcher.create<ChangeListener?>(ChangeListener::class.java)

  init {
    myToolState = myDefaultDescriptor.getState()

    mySeverityChooser = MySeverityChooser(myProfile.getProfileManager().getSeverityRegistrar())
    val severityPanel = mySeverityChooser.createCustomComponent(mySeverityChooser.getTemplatePresentation(), ActionPlaces.UNKNOWN)

    val tool = ObjectUtils.tryCast<BaseCommitMessageInspection?>(myToolState.getTool().getTool(), BaseCommitMessageInspection::class.java)
    myOptionsConfigurable = if (tool != null) tool.createOptionsConfigurable() else null
    val options = if (myOptionsConfigurable != null) myOptionsConfigurable.getComponent()
    else myToolState.getAdditionalConfigPanel(this,
                                              myProject)

    myMainPanel = CommitMessageInspectionDetailsPanel(severityPanel, options)

    reset()
  }

  val key: HighlightDisplayKey get() = myDefaultDescriptor.key

  fun update() {
    mySeverityChooser.setChosen(ScopesAndSeveritiesTable.getSeverity(mutableListOf<ScopeToolState?>(myToolState)))
    GuiUtils.enableChildren(myToolState.isEnabled, myMainPanel.component)
  }

  fun addListener(listener: ChangeListener) {
    myEventDispatcher.addListener(listener)
  }

  override fun createComponent(): JComponent {
    return myMainPanel.component
  }

  override fun isModified(): Boolean {
    return myOptionsConfigurable != null && myOptionsConfigurable.isModified(myProject)
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    if (myOptionsConfigurable != null) {
      myOptionsConfigurable.apply(myProject)
    }
  }

  override fun reset() {
    if (myOptionsConfigurable != null) {
      myOptionsConfigurable.reset(myProject)
    }
  }

  override fun dispose() {
    if (myOptionsConfigurable is Disposable) {
      Disposer.dispose(myOptionsConfigurable as Disposable)
    }
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
