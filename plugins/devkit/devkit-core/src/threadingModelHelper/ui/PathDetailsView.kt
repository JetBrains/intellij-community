// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

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
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzer
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
  selectedPath: LockReqsAnalyzer.Companion.ExecutionPath?,
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
  }
  else {
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
            selectedPath.methodChain.forEachIndexed { index, method ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "${index + 1}. ",
                  fontFamily = FontFamily.Monospace,
                  style = JewelTheme.defaultTextStyle,
                  color = JewelTheme.globalColors.text.disabled
                )
                Text(
                  text = "${method.containingClass?.qualifiedName}.${method.name}",
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
                tint = JewelTheme.globalColors.text.error
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = when (selectedPath.lockRequirement.type) {
                  LockReqsAnalyzer.Companion.LockCheckType.ANNOTATION ->
                    "Method has @RequiresReadLock annotation"
                  LockReqsAnalyzer.Companion.LockCheckType.ASSERTION ->
                    "Method calls ThreadingAssertions.assertReadAccess()"
                },
                style = JewelTheme.defaultTextStyle,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // Action buttons
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        DefaultButton(
          onClick = {
            val method = selectedPath.lockRequirement.method
            val psiFile = method.containingFile
            if (psiFile != null && psiFile.virtualFile != null) {
              OpenFileDescriptor(
                project,
                psiFile.virtualFile,
                method.textOffset
              ).navigate(true)
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
            CopyPasteManager.getInstance()
              .setContents(StringSelection(selectedPath.pathString))
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
}
