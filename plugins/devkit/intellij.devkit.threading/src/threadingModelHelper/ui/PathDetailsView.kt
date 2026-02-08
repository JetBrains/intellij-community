// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.datatransfer.StringSelection

@Composable
internal fun PathDetailsView(
  project: Project,
  selectedPath: ExecutionPath?,
) {
  if (selectedPath == null) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          key = AllIconsKeys.General.InspectionsEye,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = JewelTheme.globalColors.text.disabled
        )
        Text(
          text = "Select a path to view details",
          color = JewelTheme.globalColors.text.disabled
        )
      }
    }
    return
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(text = "Execution Path Details")

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .background(
          JBUI.CurrentTheme.CustomFrameDecorations.paneBackground().toComposeColor(),
          RoundedCornerShape(8.dp)
        )
        .border(
          width = 1.dp,
          color = JewelTheme.globalColors.borders.normal,
          shape = RoundedCornerShape(8.dp)
        )
        .padding(16.dp)
    ) {
      SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          selectedPath.methodChain.forEachIndexed { index, call ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${index + 1}. ",
                fontFamily = FontFamily.Monospace,
                style = JewelTheme.defaultTextStyle,
                color = JewelTheme.globalColors.text.disabled
              )
              val cls = call.containingClassName ?: "Unknown"
              Text(
                text = "$cls.${call.methodName}(${call.sourceLocation})",
                fontFamily = FontFamily.Monospace,
                style = JewelTheme.defaultTextStyle
              )
            }
          }

          Divider(orientation = Orientation.Horizontal)

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              key = AllIconsKeys.Debugger.Db_set_breakpoint,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = when (selectedPath.lockRequirement.constraintType) {
                ConstraintType.READ -> JewelTheme.globalColors.text.info
                ConstraintType.NO_READ -> JewelTheme.globalColors.text.error
                ConstraintType.WRITE, ConstraintType.WRITE_INTENT -> JewelTheme.globalColors.text.error
                ConstraintType.EDT, ConstraintType.BGT -> JewelTheme.globalColors.text.normal
              }
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = lockRequirementText(selectedPath),
              style = JewelTheme.defaultTextStyle,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      DefaultButton(
        onClick = {
          val element = selectedPath.lockRequirement.source
          val psiFile = element.containingFile
          val vFile = psiFile?.virtualFile
          if (vFile != null) {
            WriteIntentReadAction.run {
              OpenFileDescriptor(project, vFile, element.textOffset).navigate(true)
            }
          }
        }
      ) {
        Icon(
          key = AllIconsKeys.Actions.EditSource,
          contentDescription = null,
          modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text("Go to Lock Check")
      }

      OutlinedButton(
        onClick = {
          val chain = selectedPath.methodChain.joinToString("\n") { call ->
            val cls = call.containingClassName ?: "Unknown"
            "$cls.${call.methodName}(${call.sourceLocation})"
          }
          val requirement = when (selectedPath.lockRequirement.constraintType) {
            ConstraintType.READ -> "RequiresReadLock"
            ConstraintType.NO_READ -> "RequiresNoReadAccess"
            ConstraintType.WRITE -> "RequiresWriteLock"
            ConstraintType.WRITE_INTENT -> "RequiresWriteIntentLock"
            ConstraintType.EDT -> "RequiresEdt"
            ConstraintType.BGT -> "RequiresBackgroundThread"
          }
          val text = "$chain\n-> $requirement"
          CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
      ) {
        Icon(
          key = AllIconsKeys.Actions.Copy,
          contentDescription = null,
          modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Copy Path")
      }
    }
  }
}

private fun lockRequirementText(path: ExecutionPath): String {
  val reason = path.lockRequirement.requirementReason.name.lowercase().replaceFirstChar { it.titlecase() }
  val type = when (path.lockRequirement.constraintType) {
    ConstraintType.READ -> "Requires read lock"
    ConstraintType.NO_READ -> "Requires no-read access"
    ConstraintType.WRITE -> "Requires write lock"
    ConstraintType.WRITE_INTENT -> "Requires write-intent lock"
    ConstraintType.EDT -> "Requires EDT"
    ConstraintType.BGT -> "Requires background thread"
  }
  val speculative = if (path.isSpeculative) " (speculative)" else ""
  return "$type ($reason)$speculative"
}