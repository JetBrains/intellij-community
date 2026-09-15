// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.internal.ui

import com.intellij.ide.audioCues.AudioCue
import com.intellij.ide.audioCues.AudioCuePlayer
import com.intellij.ide.audioCues.getAudioCues
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.Action
import javax.swing.JComponent

@Suppress("HardCodedStringLiteral")
internal class PlayTestSoundAction : AnAction(), DumbAware {
  override fun actionPerformed(event: AnActionEvent) {
    PlaySoundDialog(event.project).show()
  }

  private class PlaySoundDialog(project: Project?) : DialogWrapper(project, true, IdeModalityType.MODELESS) {
    init {
      init()
      title = "Play Test Sound"
      setOKButtonText("Close")
    }

    override fun createCenterPanel(): JComponent = panel {
      val checkBoxes = mutableListOf<Pair<AudioCue, JBCheckBox>>()
      for (cue in getAudioCues()) {
        row {
          checkBox("").also { checkBoxes.add(cue to it.component) }
          button(cue.title) { AudioCuePlayer.getInstance().preview(cue) }
        }
      }
      row {
        button("Play All Selected") {
          val selected = checkBoxes.filter { it.second.isSelected }.map { it.first }
          if (selected.isNotEmpty()) AudioCuePlayer.getInstance().preview(*selected.toTypedArray())
        }
      }
    }

    override fun createActions(): Array<Action?> = arrayOf(okAction)
  }
}
