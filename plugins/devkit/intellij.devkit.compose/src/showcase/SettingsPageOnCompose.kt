// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.compose.ComposeSearchableConfigurable
import org.jetbrains.annotations.NonNls
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedSplitButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow

/**
 * Example Settings page implemented with Compose UI.
 *
 * This configurable shows how to build an IntelliJ Settings page with JetBrains Jewel Compose components.
 *
 * This page is only an example of composing UI for Settings and does not include any functionality.
 * See [ComposeContent] for the layout.
 */
class SettingsPageOnCompose() : ComposeSearchableConfigurable() {

  override fun getDisplayName(): @NlsContexts.ConfigurableName String? = DevkitComposeBundle.message("configurable.name.settings.page.on.compose")

  override fun isModified(): Boolean = false

  /**
   * Composable content of this example Settings page.
   *
   * The layout contains:
   * - a header banner;
   * - a section with sample checkboxes;
   * - a section with sample buttons.
   */
  @Composable
  override fun ComposeContent() {
    ComposeFoundationFlags.isNewContextMenuEnabled = false
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

      HeaderWithAnimation()

      GroupHeader("Section with checkboxes")
      Checkboxes()

      GroupHeader("Section with buttons")
      Buttons()

      GroupHeader("Section with text field")
      TextFields()
    }
  }

  @Composable
  fun HeaderWithAnimation() {
    val infinite = rememberInfiniteTransition(label = "header")
    val t by infinite.animateFloat(
      initialValue = 0f, targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
      label = "shimmer"
    )
    val headerBrush = Brush.linearGradient(
      colors = listOf(Color(0xFF6D89FF), Color(0xFF7FE0FF), Color(0xFF6D89FF)),
      start = Offset(0f, 0f),
      end = Offset(900f * t, 300f)
    )

    Row(modifier = Modifier
      .fillMaxWidth()
      .background(headerBrush)
      .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {}
  }

  @Composable
  fun Checkboxes() {
    var checked by remember { mutableStateOf(ToggleableState.Off) }
    TriStateCheckboxRow(
      "Checkbox turns on/off something",
      checked,
      onClick = {
        checked =
          when (checked) {
            ToggleableState.On -> ToggleableState.Off
            else -> ToggleableState.On
          }
      },
    )

    var checked2 by remember { mutableStateOf(ToggleableState.Off) }
    TriStateCheckboxRow(
      "Another checkbox",
      checked2,
      onClick = {
        checked2 =
          when (checked2) {
            ToggleableState.On -> ToggleableState.Off
            else -> ToggleableState.On
          }
      },
    )
  }

  @Composable
  fun Buttons() {
    val items = remember { listOf("This is", "A menu", "Item 3") }
    var selected by remember { mutableStateOf(items.first()) }
    Row(Modifier.height(50.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      OutlinedSplitButton(
        onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
        secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
        content = { Text("Split button") },
        menuContent = {
          items.forEach {
            selectableItem(
              selected = selected == it,
              onClick = {
                selected = it
                JewelLogger.getInstance("Jewel").warn("Item clicked: $it")
              },
            ) {
              Text(it)
            }
          }
        },
      )
      DefaultButton(onClick = {}) { Text("Simple button") }
    }
  }

  @Composable
  fun TextFields() {
    val state1 = rememberTextFieldState("")
    TextField(state = state1, modifier = Modifier.width(200.dp), readOnly = false)

  }

  override fun apply() {}

  override fun getId(): @NonNls String {
    return "com.intellij.devkit.compose.showcase.SettingsPageOnCompose"
  }
}