// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import org.jetbrains.jewel.ui.util.isDark
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.ArrayDeque
import javax.swing.KeyStroke
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.intellij.devkit.compose.showcase.util.Kodee3DSpinning
import com.intellij.devkit.compose.showcase.util.KodeeAngry2D
import com.intellij.devkit.compose.showcase.util.KodeeDance2D
import com.intellij.devkit.compose.showcase.util.KodeeNoticeMe2D
import com.intellij.devkit.compose.showcase.util.KodeeSitDown2D
import com.intellij.devkit.compose.showcase.util.KodeeStanding2D
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Preview
@Composable
internal fun ComposeShowcase() {
  Column(
    verticalArrangement = Arrangement.spacedBy(15.dp),
    modifier = Modifier.padding(10.dp)
  ) {
    Title()
    Text("This is Compose bundled inside IntelliJ Platform!")
    Row {
      VerticallyScrollableContainer(
        modifier = Modifier.weight(1f)
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
          CheckBox()
          RadioButton()
          Buttons()
          Label()
          SelectableText()
          Tabs()
          LinkLabels()
          TextFieldSimple()
          TextFieldWithButton()
          TooltipAreaSimple()
          InfiniteAnimation()
          KodeeShowcase()
        }
      }
    }
  }
}

@Composable
private fun InfiniteAnimation() {
  val transition = rememberInfiniteTransition("coso")
  val animatedAlpha by
  transition.animateFloat(
    0f,
    1f,
    infiniteRepeatable(tween(durationMillis = 1000, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
  )
  Box(Modifier.alpha(animatedAlpha)) {
    Text("Animation!")
  }
}

@Composable
private fun Title() {
  Column {
    Text("Showcase of Jewel components", fontSize = 15.sp)
    Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun CheckBox() {
  var checkedState by remember { mutableStateOf(false) }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Checkbox:")
    CheckboxRow(
      "checkBox",
      checkedState,
      onCheckedChange = {
        checkedState = it
      }
    )
  }
}


@Composable
private fun RadioButton() {
  var selectedRadioButton by remember { mutableIntStateOf(1) }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("radioButton")
    RadioButtonRow(
      "Value 1",
      selected = selectedRadioButton == 0,
      onClick = {
        selectedRadioButton = 0
      }
    )
    RadioButtonRow(
      "Value 2",
      selected = selectedRadioButton == 1,
      onClick = {
        selectedRadioButton = 1
      }
    )
  }
}

@Composable
private fun Label() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Text("label:")
    Text("Some label")
  }
}

@Composable
private fun SelectableText() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    SelectionContainer {
      Text("Selectable text")
    }
  }
}

@Composable
private fun Buttons() {
  Row(
    horizontalArrangement = Arrangement.spacedBy(20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    var state1 by remember { mutableIntStateOf(0) }
    OutlinedButton(onClick = {
      state1++
    }) {
      Text("Click me #$state1")
    }

    var state2 by remember { mutableIntStateOf(0) }
    DefaultButton(onClick = {
      state2++
    }) {
      Text("Click me #$state2")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      var state3 by remember { mutableIntStateOf(0) }
      var focused by remember { mutableStateOf(false) }
      IconButton(
        onClick = { state3++ },
        modifier = Modifier
          .size(18.dp)
          .onFocusEvent { focused = it.isFocused }
          .background(if (focused) JBUI.CurrentTheme.Focus.focusColor().toComposeColor() else Color.Unspecified, RoundedCornerShape(4.dp))
      ) {
        Icon(AllIconsKeys.General.FitContent, contentDescription = null, iconClass = AllIcons::class.java)
      }
      Text("← Click me #$state3")
    }
  }
}

@Composable
private fun Tabs() {
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  val tabIds by remember { mutableStateOf((1..12).toList()) }

  val tabs by remember {
    derivedStateOf {
      tabIds.mapIndexed { index, id ->
        TabData.Default(
          selected = index == selectedTabIndex,
          content = {
            Text("Tab $id")
          },
          closable = false,
          onClick = { selectedTabIndex = index },
        )
      }
    }
  }

  TabStrip(tabs, JewelTheme.defaultTabStyle)
}

@Composable
private fun LinkLabels() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Labels:")
    Link("Link", onClick = {
      // do nothing
    })
  }
}

@Composable
private fun TextFieldSimple() {
  val textFieldState = rememberTextFieldState()
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Text field:")
    TextField(
      textFieldState,
      modifier = Modifier.padding(5.dp)
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextFieldWithButton() {
  val textFieldState = rememberTextFieldState()
  var fileExists by remember { mutableStateOf(true) }

  LaunchedEffect(textFieldState) {
    delay(300.milliseconds)
    withContext(IO) {
      fileExists = textFieldState.text.isEmpty() || Path.of(textFieldState.text.toString()).exists()
    }
  }

  fun chooseFile() {
    val descriptor = FileChooserDescriptorFactory.singleFileOrDir()
    descriptor.title = UIBundle.message("file.chooser.default.title")
    FileChooser.chooseFile(descriptor, null, null) {
      textFieldState.edit { replace(0, textFieldState.text.length, it.path) }
    }
  }

  val openFileChooserHint = UIBundle.message("component.with.browse.button.browse.button.tooltip.text") + " (${
    KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))
  })"

  Row(
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Choose file or folder:")
    TextField(
      textFieldState,
      modifier = Modifier
        .padding(5.dp)
        .height(28.dp)
        .onKeyEvent {
          if (it.isShiftPressed && it.key == Key.Enter) true.also { chooseFile() } else false
        },
      outline = if (fileExists) Outline.None else Outline.Error,
      placeholder = {
        Text(
          text = openFileChooserHint,
          color = JBUI.CurrentTheme.ContextHelp.FOREGROUND.toComposeColor(),
          fontSize = (JewelTheme.defaultTextStyle.fontSize.value - 2).sp,
        )
      },
      trailingIcon = {
        Tooltip(
          tooltip = { TooltipSimple { Text(openFileChooserHint, color = JewelTheme.tooltipStyle.colors.content) } }
        ) {
          IconButton({ chooseFile() }, Modifier.size(18.dp).pointerHoverIcon(PointerIcon.Hand).focusProperties { canFocus = false }) {
            AllIcons.General.OpenDisk
            Icon(AllIconsKeys.General.OpenDisk, openFileChooserHint, iconClass = AllIcons::class.java)
          }
        }
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TooltipAreaSimple() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Hover with tooltip example:")
    TooltipArea({ TooltipSimple { Text("Sample text") } }) {

      var hovered by remember { mutableStateOf(false) }
      val cornerSize = animateDpAsState(if (hovered) 28.dp else 4.dp)

      Box(Modifier
            .onHover { hovered = it }
            .border(
              width = 2.dp,
              color = JBUI.CurrentTheme.Button.focusBorderColor(true).toComposeColor(),
              shape = RoundedCornerShape(cornerSize.value),
            )
      ) {
        Text("Hovered: $hovered", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
      }
    }
  }
}

@Composable
private fun TooltipSimple(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier
      .shadow(
        elevation = JewelTheme.tooltipStyle.metrics.shadowSize,
        shape = RoundedCornerShape(JewelTheme.tooltipStyle.metrics.cornerSize),
        ambientColor = JewelTheme.tooltipStyle.colors.shadow,
        spotColor = Color.Transparent,
      )
      .background(
        color = JewelTheme.tooltipStyle.colors.background,
        shape = RoundedCornerShape(JewelTheme.tooltipStyle.metrics.cornerSize),
      )
      .border(
        width = JewelTheme.tooltipStyle.metrics.borderWidth,
        color = JewelTheme.tooltipStyle.colors.border,
        shape = RoundedCornerShape(JewelTheme.tooltipStyle.metrics.cornerSize),
      )
      .padding(JewelTheme.tooltipStyle.metrics.contentPadding),
  ) {
    OverrideDarkMode(JewelTheme.tooltipStyle.colors.background.isDark()) {
      content()
    }
  }
}

@Composable
private fun KodeeShowcase() {
  var isLarge by remember { mutableStateOf(false) }
  var is3DMode by remember { mutableStateOf(false) }
  val clickTimes = remember { ArrayDeque<Long>() }
  val scope = rememberCoroutineScope()
  val kodeeSize = if (isLarge) 160.dp else 80.dp

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(bottom = if (isLarge) 36.dp else 18.dp),
  ) {
    Text("Kodee", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Size:")
      OutlinedButton(enabled = isLarge, onClick = { isLarge = false }) { Text("−") }
      OutlinedButton(enabled = !isLarge, onClick = { isLarge = true }) { Text("+") }
    }

    FlowRow(
      modifier = Modifier
        .fillMaxWidth()
        .clickable {
          if (is3DMode) return@clickable
          val now = System.currentTimeMillis()
          clickTimes.addLast(now)
          while (clickTimes.size > 5) clickTimes.removeFirst()
          if (clickTimes.size == 5 && now - clickTimes.first() < 1500L) {
            clickTimes.clear()
            is3DMode = true
            scope.launch {
              delay(10.seconds)
              is3DMode = false
            }
          }
        },
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (is3DMode) {
        Kodee3DSpinning(Modifier.size(kodeeSize))
      }
      else {
        KodeeStanding2D(Modifier.size(kodeeSize))
        KodeeDance2D(Modifier.size(kodeeSize))
        KodeeSitDown2D(Modifier.size(kodeeSize))
        KodeeNoticeMe2D(Modifier.size(kodeeSize))
        KodeeAngry2D(Modifier.size(kodeeSize))
      }
    }
  }
}
