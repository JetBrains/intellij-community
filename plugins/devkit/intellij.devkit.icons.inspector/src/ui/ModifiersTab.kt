// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.EditableModifier
import com.intellij.devkit.icons.inspector.model.EditableModifierKind
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.InspectorNode
import com.intellij.devkit.icons.inspector.model.InspectorNodeKind
import com.intellij.devkit.icons.inspector.model.InspectorNodeOrigin
import com.intellij.devkit.icons.inspector.model.ModifierEdit
import com.intellij.devkit.icons.inspector.model.ModifierEditingState
import com.intellij.devkit.icons.inspector.model.ModifierField
import com.intellij.devkit.icons.inspector.model.ModifierFieldType
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

private val ERROR_RED_LIGHT = Color(0xFFC7222D)
private val ERROR_RED_DARK = Color(0xFFFF7A7A)

@Composable
private fun errorColor() = if (JewelTheme.isDark) ERROR_RED_DARK else ERROR_RED_LIGHT

@Composable
internal fun ModifiersTab(controller: IconInspectorController, node: InspectorNode?, editing: ModifierEditingState?) {
  val info = JewelTheme.globalColors.text.info
  if (editing == null) {
    Text(DevkitIconsInspectorBundle.message("ui.modifiers.unavailable"), color = info, modifier = Modifier.padding(12.dp))
    return
  }
  VerticallyScrollableContainer(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      EditingHeader(controller, editing)
      val isApiLayer = node != null && node.kind == InspectorNodeKind.LAYER && node.origin == InspectorNodeOrigin.ICONS_API
      if (node == null || (node.modifiers.isEmpty() && !isApiLayer)) {
        Text(DevkitIconsInspectorBundle.message("ui.modifiers.select.layer"), color = info)
      }
      else {
        if (node.modifiers.isEmpty()) Text(DevkitIconsInspectorBundle.message("ui.modifiers.none"), color = info)
        node.modifiers.forEachIndexed { index, modifier ->
          key(node.id, modifier.id) { ModifierCard(controller, node.id, modifier, index, node.modifiers.size) }
        }
        AddModifier(controller, node.id)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditingHeader(controller: IconInspectorController, editing: ModifierEditingState) {
  val dark = JewelTheme.isDark
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      if (editing.editCount == 0) DevkitIconsInspectorBundle.message("ui.modifiers.original")
      else DevkitIconsInspectorBundle.message("ui.modifiers.edited", editing.editCount),
      color = if (editing.editCount > 0) amberColor(dark) else JewelTheme.globalColors.text.info,
    )
    val apply: @Composable () -> Unit = {
      CheckboxRow(
        checked = editing.appliedToComponent,
        onCheckedChange = controller::setAppliedToComponent,
        enabled = editing.canApplyToComponent,
      ) { Text(DevkitIconsInspectorBundle.message("ui.modifiers.apply")) }
    }
    if (editing.canApplyToComponent) apply()
    else Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.modifiers.apply.unsupported")) }) { apply() }
    OutlinedButton(onClick = controller::revertModifierEdits, enabled = editing.editCount > 0 || editing.appliedToComponent) {
      Text(DevkitIconsInspectorBundle.message("ui.modifiers.revert"))
    }
  }
  editing.message?.let {
    Text(
      "\u26A0 $it",
      color = amberColor(dark),
      modifier = Modifier.fillMaxWidth()
        .background(amberColor(dark).copy(alpha = 0.14f), RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun ModifierCard(controller: IconInspectorController, layerId: String, modifier: EditableModifier, index: Int, count: Int) {
  val info = JewelTheme.globalColors.text.info
  val readOnly = modifier.kind == EditableModifierKind.READ_ONLY
  var cardError by remember(layerId, modifier.id) { mutableStateOf<String?>(null) }
  Column(
    Modifier.fillMaxWidth()
      .border(1.dp, info.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
      .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(modifier.title, color = if (readOnly) info else Color.Unspecified, modifier = Modifier.weight(1f, fill = false))
      Text(
        if (readOnly) DevkitIconsInspectorBundle.message("ui.modifiers.read.only") else modifier.kind.name,
        color = info,
        fontSize = 9.sp,
        maxLines = 1,
        modifier = Modifier.border(1.dp, info.copy(alpha = 0.5f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
      )
      Box(Modifier.weight(1f))
      if (!readOnly) {
        Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.modifiers.up")) }) {
          OutlinedButton(onClick = { cardError = controller.editModifiers(ModifierEdit.Move(layerId, modifier.id, index - 1)) }, enabled = index > 0) { Text("\u25B2") }
        }
        Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.modifiers.down")) }) {
          OutlinedButton(onClick = { cardError = controller.editModifiers(ModifierEdit.Move(layerId, modifier.id, index + 1)) }, enabled = index < count - 1) { Text("\u25BC") }
        }
        Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.modifiers.remove")) }) {
          OutlinedButton(onClick = { cardError = controller.editModifiers(ModifierEdit.Remove(layerId, modifier.id)) }) { Text("\u2715") }
        }
      }
    }
    cardError?.let { Text(it, color = errorColor(), fontSize = 11.sp) }
    modifier.fields.forEach { field ->
      FieldRow(controller, layerId, modifier, field, readOnly)
    }
  }
}

@Composable
private fun FieldRow(controller: IconInspectorController, layerId: String, modifier: EditableModifier, field: ModifierField, readOnlyKind: Boolean) {
  val info = JewelTheme.globalColors.text.info
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
    Text(field.label, color = info, modifier = Modifier.widthIn(max = 120.dp).weight(0.3f).padding(top = 4.dp))
    Box(Modifier.weight(0.7f)) {
      if (readOnlyKind || !field.editable) {
        Text(field.value, color = info, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
      }
      else {
        key(layerId, modifier.id, field.key) { FieldEditor(controller, layerId, modifier, field) }
      }
    }
  }
}

private val UNIT_REGEX = Regex("^-?\\d+(\\.\\d+)?(dp|px)$")
private val COLOR_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

private fun parseColor(text: String): Color? {
  if (!COLOR_REGEX.matches(text)) return null
  val hex = text.substring(1)
  val argb = if (hex.length == 6) 0xFF000000L or hex.toLong(16) else hex.toLong(16)
  return Color(argb.toInt())
}

@Composable
private fun FieldEditor(controller: IconInspectorController, layerId: String, modifier: EditableModifier, field: ModifierField) {
  var error by remember { mutableStateOf<String?>(null) }
  val textState = rememberTextFieldState(field.value)
  // Value the model is known to hold (initially, or after our last successful commit / adopted external change).
  var lastKnown by remember { mutableStateOf(field.value) }
  var dirty by remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }

  fun setText(value: String) {
    lastKnown = value
    dirty = false
    textState.edit { replace(0, length, value) }
  }

  // Model republishes happen constantly; only touch the text when it cannot clobber the user's input.
  LaunchedEffect(field.value) {
    if (field.value != lastKnown) {
      // changed externally (e.g. Revert) or by an edit elsewhere
      setText(field.value)
      error = null
    }
    else if (!focused && !dirty && textState.text.toString() != field.value) {
      setText(field.value)
    }
  }
  LaunchedEffect(textState) {
    snapshotFlow { textState.text.toString() }.collect { if (it != lastKnown) dirty = true }
  }

  fun commit(value: String) {
    if (value == lastKnown) { error = null; dirty = false; return }
    val local = when (field.type) {
      ModifierFieldType.NUMBER -> if (value.toDoubleOrNull() == null) DevkitIconsInspectorBundle.message("ui.modifiers.invalid.number") else null
      ModifierFieldType.UNIT -> if (!UNIT_REGEX.matches(value)) DevkitIconsInspectorBundle.message("ui.modifiers.invalid.unit") else null
      ModifierFieldType.COLOR -> if (!COLOR_REGEX.matches(value)) DevkitIconsInspectorBundle.message("ui.modifiers.invalid.color") else null
      else -> null
    }
    error = local ?: controller.editModifiers(ModifierEdit.SetField(layerId, modifier.id, field.key, value))
    if (error == null) { lastKnown = value; dirty = false }
  }

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (field.type == ModifierFieldType.CHOICE) {
      ListComboBox(
        items = field.options,
        selectedIndex = field.options.indexOf(field.value).coerceAtLeast(0),
        onSelectedItemChange = { commit(field.options[it]) },
        outline = if (error != null) Outline.Error else Outline.None,
      )
    }
    else {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (field.type == ModifierFieldType.COLOR) {
          val color = parseColor(textState.text.toString())
          Box(
            Modifier.size(18.dp)
              .background(color ?: Color.Transparent, RoundedCornerShape(3.dp))
              .border(1.dp, JewelTheme.globalColors.text.info.copy(alpha = 0.6f), RoundedCornerShape(3.dp)),
          )
        }
        TextField(
          state = textState,
          modifier = Modifier.weight(1f)
            .onFocusChanged {
              val was = focused
              focused = it.isFocused
              if (was && !it.isFocused) commit(textState.text.toString())
            }
            .onPreviewKeyEvent {
              if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) { commit(textState.text.toString()); true } else false
            },
          textStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace),
          outline = if (error != null) Outline.Error else Outline.None,
        )
        if (modifier.kind == EditableModifierKind.ALPHA && field.type == ModifierFieldType.NUMBER) {
          val current = textState.text.toString().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
          Slider(
            value = current,
            onValueChange = { v -> textState.edit { replace(0, length, String.format("%.2f", v)) } },
            onValueChangeFinished = { commit(textState.text.toString()) },
            modifier = Modifier.weight(1f),
            valueRange = 0f..1f,
          )
        }
      }
    }
    error?.let { Text(it, color = errorColor(), fontSize = 11.sp) }
  }
}

@Composable
private fun AddModifier(controller: IconInspectorController, layerId: String) {
  var error by remember { mutableStateOf<String?>(null) }
  val kinds = EditableModifierKind.entries.filter { it != EditableModifierKind.READ_ONLY }
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Dropdown(
      menuContent = {
        kinds.forEach { kind ->
          selectableItem(selected = false, onClick = { error = controller.editModifiers(ModifierEdit.Add(layerId, kind)) }) { Text(kind.name) }
        }
      },
    ) {
      Text(DevkitIconsInspectorBundle.message("ui.modifiers.add"))
    }
    error?.let { Text(it, color = errorColor(), fontSize = 11.sp) }
  }
}
