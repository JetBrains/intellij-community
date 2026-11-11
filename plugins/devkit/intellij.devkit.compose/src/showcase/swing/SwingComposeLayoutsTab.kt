// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.devkit.compose.showcase.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.intellij.platform.compose.swing.components.ActionLink
import com.intellij.platform.compose.swing.components.BrowserLink
import com.intellij.platform.compose.swing.components.ComboBox
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.platform.compose.swing.components.DropDownLink
import com.intellij.platform.compose.swing.components.FormPanel
import com.intellij.platform.compose.swing.components.SearchTextField
import com.intellij.platform.compose.swing.components.ThreeStateCheckBox
import com.intellij.platform.compose.swing.components.ThreeStateCheckBoxState
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.preferredSize
import java.awt.FlowLayout
import javax.swing.BoxLayout

/** The Layouts tab: the basic components, and the layouts of the library with live settings. One page shows one topic. */
@Composable
internal fun LayoutsTab(modifier: SwingModifier) {
  val pages = remember {
    listOf(
      ShowcasePage("Buttons", "Push buttons, check boxes, radio buttons and toggle buttons.") { ButtonsPage() },
      ShowcasePage("Text input", "Text fields and a text area. The labels show the state that the fields hold.") { TextInputPage() },
      ShowcasePage("Choosers", "Controls that select one value from a list or a range.") { ChoosersPage() },
      ShowcasePage("Links and text", "Labels, links and comments.") { LinksPage() },
      ShowcasePage("Row", "A Row puts its children side by side. Change the arrangement and the alignment to move them.") { RowPage() },
      ShowcasePage("Column", "A Column puts its children one under the other.") { ColumnPage() },
      ShowcasePage("Box", "A Box puts its children on top of each other.") { BoxPage() },
      ShowcasePage("Weight", "In a Row or a Column, a weight divides the free space between the children.") { WeightPage() },
      ShowcasePage(
        "Nested",
        "A Row of Columns makes a small form. A Row with Arrangement.End puts the buttons at the right.",
      ) { NestedPage() },
      ShowcasePage("Swing layouts", "Panel uses a Swing layout manager for its children.") { SwingLayoutsPage() },
      ShowcasePage("FormPanel", "FormPanel aligns labels, controls and comments as a Kotlin UI DSL panel does.") { FormPanelPage() },
    )
  }
  ShowcasePages(pages, modifier)
}

// region Components

@Composable
private fun ColumnScope.ButtonsPage() {
  var clicks by remember { mutableIntStateOf(0) }
  LabeledRow("Button") {
    Button(text = "Click me", onClick = { clicks++ })
    Button(text = "Disabled", onClick = {}, style = { enabled(false) })
    Label("Clicked $clicks times")
  }

  var checked by remember { mutableStateOf(true) }
  LabeledRow("CheckBox") {
    CheckBox(text = "Check me", checked = checked, onCheckedChange = { checked = it })
    CheckBox(text = "Disabled", checked = checked, onCheckedChange = {}, style = { enabled(false) })
  }

  var threeState by remember { mutableStateOf(ThreeStateCheckBoxState.INDETERMINATE) }
  LabeledRow("ThreeStateCheckBox") {
    ThreeStateCheckBox(text = "Three states", state = threeState, onStateChange = { threeState = it })
    Label(threeState.name)
  }

  var radio by remember { mutableIntStateOf(0) }
  LabeledRow("RadioGroup") {
    RadioGroup(selectedIndex = radio, onSelectionChange = { radio = it }, axis = BoxLayout.X_AXIS) {
      option("First")
      option("Second")
      option("Third")
    }
  }

  var toggled by remember { mutableStateOf(false) }
  LabeledRow("ToggleButton") {
    ToggleButton(text = if (toggled) "On" else "Off", selected = toggled, onSelectedChange = { toggled = it })
  }
}

@Composable
private fun ColumnScope.TextInputPage() {
  var text by remember { mutableStateOf("Some text") }
  LabeledRow("TextField") {
    TextField(value = text, onValueChange = { text = it }, columns = 20)
    Label("Length: ${text.length}")
  }

  var password by remember { mutableStateOf(CharArray(0)) }
  LabeledRow("PasswordField") {
    PasswordField(value = password, onValueChange = { password = it }, columns = 20)
    Label("Length: ${password.size}")
  }

  var search by remember { mutableStateOf("") }
  LabeledRow("SearchTextField") {
    SearchTextField(text = search, onTextChange = { search = it }, modifier = SwingModifier.width(250.dp))
  }

  var area by remember { mutableStateOf("A text area.\nIt wraps long lines at word boundaries, so you can type a long sentence here.") }
  LabeledRow("TextArea") {
    ScrollPane(style = { preferredSize(300.dp, 80.dp) }) {
      Viewport { TextArea(value = area, onValueChange = { area = it }, lineWrap = true, wrapStyleWord = true) }
    }
  }
}

@Composable
private fun ColumnScope.ChoosersPage() {
  var fruit by remember { mutableStateOf(FRUITS.first()) }
  LabeledRow("ComboBox") {
    ComboBox(items = FRUITS, selectedItem = fruit, onSelectedItemChange = { if (it != null) fruit = it })
  }

  var linkFruit by remember { mutableStateOf(FRUITS.first()) }
  LabeledRow("DropDownLink") {
    DropDownLink(items = FRUITS, selectedItem = linkFruit, onSelectedItemChange = { linkFruit = it })
  }

  var count by remember { mutableIntStateOf(5) }
  LabeledRow("Spinner") {
    Spinner(value = count, onValueChange = { count = it.toInt() }, min = 0, max = 10)
  }

  var progress by remember { mutableIntStateOf(40) }
  LabeledRow("Slider") {
    Slider(value = progress, onValueChange = { progress = it })
  }
  LabeledRow("ProgressBar") {
    ProgressBar(value = progress, stringPainted = true)
    Comment("The slider sets the value")
  }
}

@Composable
private fun ColumnScope.LinksPage() {
  LabeledRow("Label") {
    Label("A label")
    Label("A disabled label", style = { enabled(false) })
  }

  var linkClicks by remember { mutableIntStateOf(0) }
  LabeledRow("ActionLink") {
    ActionLink(text = "Action link", onClick = { linkClicks++ })
    Label("Clicked $linkClicks times")
  }
  LabeledRow("BrowserLink") {
    BrowserLink(text = "JetBrains", url = "https://www.jetbrains.com")
  }
  LabeledRow("Comment") {
    Comment("A comment explains the control above it")
  }
}

private val FRUITS = listOf("Apple", "Banana", "Cherry", "Grape")

// endregion

// region Row, Column, Box

private enum class HorizontalArrangementItem(private val title: String, val arrangement: Arrangement.Horizontal) {
  START("Start", Arrangement.Start),
  CENTER("Center", Arrangement.Center),
  END("End", Arrangement.End),
  SPACE_BETWEEN("SpaceBetween", Arrangement.SpaceBetween),
  SPACE_AROUND("SpaceAround", Arrangement.SpaceAround),
  SPACE_EVENLY("SpaceEvenly", Arrangement.SpaceEvenly),
  SPACED_BY("spacedBy(16)", Arrangement.spacedBy(16.dp));

  override fun toString(): String = title
}

private enum class VerticalArrangementItem(private val title: String, val arrangement: Arrangement.Vertical) {
  TOP("Top", Arrangement.Top),
  CENTER("Center", Arrangement.Center),
  BOTTOM("Bottom", Arrangement.Bottom),
  SPACE_BETWEEN("SpaceBetween", Arrangement.SpaceBetween),
  SPACE_AROUND("SpaceAround", Arrangement.SpaceAround),
  SPACE_EVENLY("SpaceEvenly", Arrangement.SpaceEvenly),
  SPACED_BY("spacedBy(16)", Arrangement.spacedBy(16.dp));

  override fun toString(): String = title
}

private enum class VerticalAlignmentItem(private val title: String, val alignment: Alignment.Vertical) {
  TOP("Top", Alignment.Top),
  CENTER("CenterVertically", Alignment.CenterVertically),
  BOTTOM("Bottom", Alignment.Bottom);

  override fun toString(): String = title
}

private enum class HorizontalAlignmentItem(private val title: String, val alignment: Alignment.Horizontal) {
  START("Start", Alignment.Start),
  CENTER("CenterHorizontally", Alignment.CenterHorizontally),
  END("End", Alignment.End);

  override fun toString(): String = title
}

private enum class BoxAlignmentItem(private val title: String, val alignment: Alignment) {
  TOP_START("TopStart", Alignment.TopStart),
  TOP_CENTER("TopCenter", Alignment.TopCenter),
  TOP_END("TopEnd", Alignment.TopEnd),
  CENTER_START("CenterStart", Alignment.CenterStart),
  CENTER("Center", Alignment.Center),
  CENTER_END("CenterEnd", Alignment.CenterEnd),
  BOTTOM_START("BottomStart", Alignment.BottomStart),
  BOTTOM_CENTER("BottomCenter", Alignment.BottomCenter),
  BOTTOM_END("BottomEnd", Alignment.BottomEnd);

  override fun toString(): String = title
}

@Composable
private fun ColumnScope.RowPage() {
  var arrangement by remember { mutableStateOf(HorizontalArrangementItem.START) }
  var alignment by remember { mutableStateOf(VerticalAlignmentItem.TOP) }
  Selector("horizontalArrangement", HorizontalArrangementItem.entries, arrangement) { arrangement = it }
  Selector("verticalAlignment", VerticalAlignmentItem.entries, alignment) { alignment = it }
  Row(
    SwingModifier.fillMaxWidth().height(100.dp).demoArea(),
    horizontalArrangement = arrangement.arrangement,
    verticalAlignment = alignment.alignment,
  ) {
    DemoCell("Tall cell", SwingModifier.height(80.dp))
    Label("Label")
    Button(text = "Button", onClick = {})
    CheckBox(text = "Check box", checked = true, onCheckedChange = {})
    DemoCell("Short cell")
  }
}

@Composable
private fun ColumnScope.ColumnPage() {
  var arrangement by remember { mutableStateOf(VerticalArrangementItem.TOP) }
  var alignment by remember { mutableStateOf(HorizontalAlignmentItem.START) }
  Selector("verticalArrangement", VerticalArrangementItem.entries, arrangement) { arrangement = it }
  Selector("horizontalAlignment", HorizontalAlignmentItem.entries, alignment) { alignment = it }
  var text by remember { mutableStateOf("A text field") }
  Column(
    SwingModifier.fillMaxWidth().height(220.dp).demoArea(),
    verticalArrangement = arrangement.arrangement,
    horizontalAlignment = alignment.alignment,
  ) {
    Label("Label")
    Button(text = "Button", onClick = {})
    TextField(value = text, onValueChange = { text = it }, modifier = SwingModifier.width(240.dp), columns = 1)
    CheckBox(text = "Check box", checked = true, onCheckedChange = {})
    DemoCell("A wide cell", SwingModifier.width(160.dp))
  }
}

@Composable
private fun ColumnScope.BoxPage() {
  var alignment by remember { mutableStateOf(BoxAlignmentItem.TOP_START) }
  Selector("contentAlignment", BoxAlignmentItem.entries, alignment) { alignment = it }
  Comment("The button has align(BottomEnd), so it ignores contentAlignment")
  Box(SwingModifier.fillMaxWidth().height(160.dp).demoArea(), contentAlignment = alignment.alignment) {
    Button(text = "align(BottomEnd)", onClick = {}, modifier = SwingModifier.align(Alignment.BottomEnd))
    DemoCell("Small")
    DemoCell("Large", SwingModifier.size(200.dp, 100.dp))
  }
}

@Composable
private fun ColumnScope.WeightPage() {
  var fill by remember { mutableStateOf(true) }
  CheckBox(text = "fill = true", checked = fill, onCheckedChange = { fill = it })
  Comment("With fill = false, a child keeps its own width and takes only its place in the free space")
  Row(SwingModifier.fillMaxWidth().demoArea(), horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp)) {
    DemoCell("weight(1f)", SwingModifier.weight(1f, fill))
    DemoCell("weight(2f)", SwingModifier.weight(2f, fill))
    Button(text = "No weight", onClick = {})
  }
}

@Composable
private fun ColumnScope.NestedPage() {
  var firstName by remember { mutableStateOf("") }
  var lastName by remember { mutableStateOf("") }
  Column(SwingModifier.fillMaxWidth().demoArea(), verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp)) {
    Row(SwingModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.horizontalDefaultGap.dp)) {
      Column(SwingModifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp)) {
        Label("First name:")
        TextField(value = firstName, onValueChange = { firstName = it }, modifier = SwingModifier.fillMaxWidth(), columns = 1)
      }
      Column(SwingModifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp)) {
        Label("Last name:")
        TextField(value = lastName, onValueChange = { lastName = it }, modifier = SwingModifier.fillMaxWidth(), columns = 1)
      }
    }
    Row(
      SwingModifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp, Alignment.End),
    ) {
      Button(text = "Clear", onClick = { firstName = ""; lastName = "" })
      Button(text = "Swap", onClick = { firstName = lastName.also { lastName = firstName } })
    }
  }
}

// endregion

// region Swing layouts

private enum class SwingLayoutItem(private val title: String) {
  FLOW("Flow"),
  GRID("Grid"),
  BORDER("Border"),
  BOX("Box");

  override fun toString(): String = title
}

@Composable
private fun ColumnScope.SwingLayoutsPage() {
  var layout by remember { mutableStateOf(SwingLayoutItem.FLOW) }
  Selector("PanelLayout", SwingLayoutItem.entries, layout) { layout = it }
  when (layout) {
    SwingLayoutItem.FLOW -> {
      Comment("PanelLayout.Flow(FlowLayout.LEFT)")
      Panel(PanelLayout.Flow(FlowLayout.LEFT), SwingModifier.fillMaxWidth().demoArea()) {
        Button(text = "Button", onClick = {})
        CheckBox(text = "Check box", checked = true, onCheckedChange = {})
        for (i in 1..4) {
          DemoCell("Item $i")
        }
      }
    }
    SwingLayoutItem.GRID -> {
      Comment("PanelLayout.Grid(rows = 2, cols = 3). Every cell has the same size")
      Panel(PanelLayout.Grid(rows = 2, cols = 3, hgap = 4.dp, vgap = 4.dp), SwingModifier.fillMaxWidth().demoArea()) {
        Button(text = "Button", onClick = {})
        for (i in 2..6) {
          DemoCell("Cell $i")
        }
      }
    }
    SwingLayoutItem.BORDER -> {
      Comment("PanelLayout.Border(). The center child takes the free space")
      Panel(PanelLayout.Border(hgap = 4.dp, vgap = 4.dp), SwingModifier.fillMaxWidth().height(160.dp).demoArea()) {
        DemoCell("north()", SwingModifier.north())
        DemoCell("west()", SwingModifier.west())
        DemoCell("center()", SwingModifier.center())
        DemoCell("east()", SwingModifier.east())
        Button(text = "south()", onClick = {}, modifier = SwingModifier.south())
      }
    }
    SwingLayoutItem.BOX -> {
      Comment("PanelLayout.Box(BoxLayout.X_AXIS). RigidArea makes a fixed gap, and Glue takes the free space")
      Panel(PanelLayout.Box(BoxLayout.X_AXIS), SwingModifier.fillMaxWidth().demoArea()) {
        DemoCell("A")
        RigidArea(24.dp, 0)
        DemoCell("B")
        Glue()
        Button(text = "C", onClick = {})
      }
    }
  }
}

@Composable
private fun ColumnScope.FormPanelPage() {
  var name by remember { mutableStateOf("") }
  var enabled by remember { mutableStateOf(true) }
  var fruit by remember { mutableStateOf(FRUITS.first()) }
  FormPanel(SwingModifier.fillMaxWidth()) {
    FormRow(label = "Name:", comment = "A comment under the row") {
      TextField(value = name, onValueChange = { name = it }, columns = 20)
    }
    FormRow(label = "A longer label:") {
      ComboBox(items = FRUITS, selectedItem = fruit, onSelectedItemChange = { if (it != null) fruit = it })
    }
    FormGroup(title = "Group") {
      FormRow {
        CheckBox(text = "Enable the option", checked = enabled, onCheckedChange = { enabled = it })
      }
      FormIndent {
        FormRow(label = "Indented:") {
          TextField(value = name, onValueChange = { name = it }, style = { enabled(enabled) }, columns = 10)
        }
      }
    }
    FormSeparator()
    FormComment("A comment row at the end of the form")
  }
}

// endregion
