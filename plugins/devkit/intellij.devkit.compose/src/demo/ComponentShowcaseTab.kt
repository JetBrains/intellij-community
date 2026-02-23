@file:OptIn(ExperimentalLayoutApi::class)

package com.intellij.devkit.compose.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.JButton
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.actionSystem.provideData
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.modifier.onActivated
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.modifier.trackComponentActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.badge.DotBadgeShape
import org.jetbrains.jewel.ui.painter.hints.Badge
import org.jetbrains.jewel.ui.painter.hints.Size
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
import org.jetbrains.jewel.ui.typography

@Composable
internal fun ComponentShowcaseTab(project: Project) {
  val bgColor by remember(JBColor.PanelBackground.rgb) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }

  VerticallyScrollableContainer {
    Row(
      modifier =
        Modifier
          .trackComponentActivation(LocalComponent.current)
          .fillMaxSize()
          .background(bgColor)
          .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ColumnOne()
      ColumnTwo(project)
    }
  }
}

@Composable
private fun RowScope.ColumnOne() {
  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    var activated by remember { mutableStateOf(false) }
    val popupData = remember { listOf("Hello", "World", "Super long string to test how overflow would work") }

    Text(
      "Here is a selection of our finest components(activated: $activated):",
      Modifier.onActivated { activated = it },
      style = JewelTheme.typography.h3TextStyle,
    )

    var selectedItem by remember { mutableIntStateOf(-1) }
    val focusRequester = remember { FocusRequester() }

    ListComboBox(
      items = popupData,
      selectedIndex = selectedItem,
      onSelectedItemChange = { selectedItem = it },
      modifier =
        Modifier
          .widthIn(min = 200.dp, max = 350.dp)
          .focusRequester(focusRequester)
          .onFirstVisible { focusRequester.requestFocus() },
    )
    ListComboBox(
      items = popupData,
      selectedIndex = selectedItem,
      onSelectedItemChange = { selectedItem = it },
      enabled = false,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      var clicks1 by remember { mutableIntStateOf(0) }
      OutlinedButton({ clicks1++ }) { Text("Outlined: $clicks1") }
      OutlinedButton({}, enabled = false) { Text("Outlined") }

      var clicks2 by remember { mutableIntStateOf(0) }
      DefaultButton({ clicks2++ }) { Text("Default: $clicks2") }
      DefaultButton({}, enabled = false) { Text("Default") }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      val state = rememberTextFieldState("")
      TextField(
        state = state,
        modifier =
          Modifier.width(200.dp).provideData {
            set(JEWEL_COMPONENT_DATA_KEY.name, "TextField")
            lazy(JEWEL_COMPONENT_DATA_KEY.name) { Math.random().toString() }
          },
        placeholder = { Text("Write something...") },
      )

      TextField(TextFieldState("Can't write here, I'm disabled"), enabled = false)
    }

    var checked by remember { mutableStateOf(false) }
    var validated by remember { mutableStateOf(false) }
    val outline = if (validated) Outline.Error else Outline.None

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      CheckboxRow(
        checked = checked,
        onCheckedChange = { checked = it },
        outline = outline,
        modifier = Modifier.provideData { set(JEWEL_COMPONENT_DATA_KEY.name, "Checkbox") },
      ) {
        Text("Hello, I am a themed checkbox")
      }

      CheckboxRow(checked = validated, onCheckedChange = { validated = it }) { Text("Show validation") }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      var index by remember { mutableIntStateOf(0) }
      RadioButtonRow(selected = index == 0, onClick = { index = 0 }, outline = outline) {
        Text("I am number one")
      }
      RadioButtonRow(selected = index == 1, onClick = { index = 1 }, outline = outline) { Text("Sad second") }
    }

    IconsShowcase()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Circular progress small:")
      CircularProgressIndicator()
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Circular progress big:")
      CircularProgressIndicatorBig()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Tooltip(
        tooltip = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(key = AllIconsKeys.General.ShowInfos, contentDescription = "Show Info icon")
            Text("This is a tooltip")
          }
        }
      ) {
        Text(
          modifier = Modifier.border(1.dp, JewelTheme.globalColors.borders.normal).padding(12.dp, 8.dp),
          text = "Hover Me!",
        )
      }
    }

    var sliderValue by remember { mutableFloatStateOf(.15f) }
    Slider(sliderValue, { sliderValue = it }, steps = 5)

    var swingButtonClicks by remember { mutableIntStateOf(0) }
    var swingButtonFocused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Swing interop:")
      SwingPanel(
        factory = {
          JButton("Click me (Swing)").apply {
            addActionListener {
              swingButtonClicks++
              requestFocusInWindow()
            }
            addFocusListener(object : java.awt.event.FocusListener {
              override fun focusGained(e: java.awt.event.FocusEvent?) {
                swingButtonFocused = true
              }
              override fun focusLost(e: java.awt.event.FocusEvent?) {
                swingButtonFocused = false
              }
            })
          }
        },
        modifier = Modifier.height(32.dp)
      )
      Text("Clicks: $swingButtonClicks, Focused: $swingButtonFocused")
    }

    var bannerStyle by remember { mutableIntStateOf(0) }
    var clickLabel by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton({ bannerStyle = (bannerStyle + 1) % 4 }) { Text("Give me a new banner!") }
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = "Clicked action: $clickLabel")
      when (bannerStyle) {
        1 -> {
          DefaultErrorBanner(text = "This is an error banner in Compose")
          InlineErrorBanner(
            text = LOREM_IPSUM_SHORT,
            iconActions = {
              iconAction(AllIconsKeys.General.Close, "Close button", "Close") {
                clickLabel = "Error Inline Action Icon clicked"
              }
            },
            style = JewelTheme.inlineBannerStyle.error,
          )
        }

        0 -> {
          DefaultSuccessBanner(text = "This is a success banner in Compose")
          InlineSuccessBanner(
            text = LOREM_IPSUL_TEXT,
            linkActions = {
              action("Action A", onClick = { clickLabel = "Success Inline Action A clicked" })
              action("Action B", onClick = { clickLabel = "Success Inline Action B clicked" })
            },
            iconActions = {
              iconAction(AllIconsKeys.General.Close, "Close button", "Close") {
                clickLabel = "Error Close Icon clicked"
              }
              iconAction(AllIconsKeys.General.Gear, "Settings button", "Settings") {
                clickLabel = "Error Gear Icon clicked"
              }
            },
            style = JewelTheme.inlineBannerStyle.success,
          )
        }

        2 -> {
          DefaultWarningBanner(text = "This is a warning banner in Compose")
          InlineWarningBanner(text = "This is a warning banner in Compose")
        }

        else -> {
          DefaultInformationBanner(text = "This is an information banner in Compose")
          InlineInformationBanner(text = "This is an information banner in Compose")
        }
      }
    }

    var selected by remember { mutableStateOf("") }
    DefaultSplitButton(
      onClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button clicked") },
      secondaryOnClick = { JewelLogger.getInstance("Jewel").warn("Outlined split button chevron clicked") },
      content = { Text("Sub menus") },
      menuContent = {
        fun MenuScope.buildSubmenus(stack: List<Int>) {
          val stackStr = stack.joinToString(".").let { if (stack.isEmpty()) it else "$it." }

          repeat(5) {
            val number = it + 1
            val itemStr = "$stackStr$number"

            if (stack.size == 4) {
              selectableItem(
                selected = selected == itemStr,
                onClick = {
                  selected = itemStr
                  JewelLogger.getInstance("Jewel").warn("Item clicked: $itemStr") },
              ) {
                Text("Item $itemStr")
              }
            } else {
              submenu(
                submenu = { buildSubmenus(stack + number) },
                content = { Text("Submenu $itemStr") },
              )
            }
          }

          separator()

          repeat(10) {
            val number = it + 1
            val itemStr = "${stackStr}other.$number"

            selectableItem(
              selected = selected == itemStr,
              onClick = {
                selected = itemStr
                JewelLogger.getInstance("Jewel").warn("Item clicked: $itemStr")
              },
            ) {
              Text("Other Item ${it + 1}")
            }
          }
        }

        buildSubmenus(emptyList())
      },
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      var selectedIndex by remember { mutableStateOf(-1) }
      RadioButtonChip(selected = selectedIndex == 0, onClick = { selectedIndex = 0 }, enabled = true) {
        Text("First")
      }

      RadioButtonChip(selected = selectedIndex == 1, onClick = { selectedIndex = 1 }, enabled = true) {
        Text("Second")
      }

      RadioButtonChip(selected = selectedIndex == 2, onClick = { selectedIndex = 2 }, enabled = true) {
        Text("Third")
      }

      Divider(Orientation.Vertical, Modifier.fillMaxHeight())

      var isChecked by remember { mutableStateOf(false) }
      ToggleableChip(checked = isChecked, onClick = { isChecked = it }, enabled = true) { Text("Toggleable") }

      var count by remember { mutableIntStateOf(1) }
      Chip(enabled = true, onClick = { count++ }) { Text("Clicks: $count") }

      Divider(Orientation.Vertical, Modifier.fillMaxHeight())

      Chip(enabled = false, onClick = {}) { Text("Disabled") }
    }
  }
}

@Composable
private fun IconsShowcase() {
  val iconBackgroundColor =
    JewelTheme.colorPalette.blueOrNull(4) ?: JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor()

  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(AllIconsKeys.Nodes.ConfigFolder, "taskGroup")
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(
        key = AllIconsKeys.Nodes.ConfigFolder,
        contentDescription = "taskGroup",
        hint = Badge(Color.Red, DotBadgeShape.Default),
      )
    }

    Box(
      Modifier.size(24.dp).background(iconBackgroundColor, shape = RoundedCornerShape(4.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(key = AllIconsKeys.Nodes.ConfigFolder, contentDescription = "taskGroup", hint = Stroke(Color.White))
    }

    Box(
      Modifier.size(24.dp).background(iconBackgroundColor, shape = RoundedCornerShape(4.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        key = AllIconsKeys.Nodes.ConfigFolder,
        contentDescription = "taskGroup",
        hints = arrayOf(Stroke(Color.White), Badge(Color.Red, DotBadgeShape.Default)),
      )
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(key = AllIconsKeys.Nodes.ConfigFolder, contentDescription = "taskGroup", hint = Size(20))
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(
        key = AllIconsKeys.Actions.Close,
        contentDescription = "An icon",
        modifier = Modifier.border(1.dp, Color.Magenta),
        hint = Size(20),
      )
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(key = AllIconsKeys.Nodes.ConfigFolder, contentDescription = "taskGroup", hint = Size(20))
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(
        key = IdeSampleIconKeys.gitHub,
        iconClass = IdeSampleIconKeys::class.java,
        modifier = Modifier.border(1.dp, Color.Magenta),
        contentDescription = "An owned icon",
      )
    }

    IconButton(onClick = {}, Modifier.size(24.dp)) {
      Icon(key = AllIconsKeys.Actions.Close, contentDescription = "Close")
    }

    IconActionButton(
      AllIconsKeys.Actions.AddList,
      "Close",
      onClick = {},
      modifier = Modifier.size(24.dp),
      hints = arrayOf(Size(24)),
      tooltip = { Text("Hello there") },
    )
  }
}

@Composable
private fun RowScope.ColumnTwo(project: Project) {
  Column(Modifier.trackActivation().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    MarkdownExample(project)

    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

    var activated by remember { mutableStateOf(false) }
    Text("activated: $activated", Modifier.onActivated { activated = it })

    val tree = remember {
      buildTree {
        addNode("root 1") {
          addLeaf("leaf 1")
          addLeaf("leaf 2")
        }
        addNode("root 2") {
          addLeaf("leaf 1")
          addNode("node 1") {
            addLeaf("leaf 1")
            addLeaf("leaf 2")
          }
        }
        addNode("root 3") {
          addLeaf("leaf 1")
          addLeaf("leaf 2")
        }
      }
    }
    LazyTree(
      tree = tree,
      modifier = Modifier.height(200.dp).fillMaxWidth(),
      onElementClick = {},
      onElementDoubleClick = {},
    ) { element ->
      Box(Modifier.fillMaxWidth()) { Text(element.data, Modifier.padding(2.dp)) }
    }
  }
}

@Composable
private fun MarkdownExample(project: Project) {
  var enabled by remember { mutableStateOf(true) }
  var selectable by remember { mutableStateOf(false) }
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    CheckboxRow("Enabled", enabled, { enabled = it })
    CheckboxRow("Selectable", selectable, { selectable = it })
  }

  InfoText("Shows the enabled/disabled styling")

  val contentColor = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled
  CompositionLocalProvider(LocalContentColor provides contentColor) {
    ProvideMarkdownStyling(project) {
      Markdown(
        """
                |Hi! This is an example of **Markdown** rendering. We support the [CommonMark specs](https://commonmark.org/)
                |out of the box, but `you` can also have _extensions_.
                |
                |For example:
                | * Images
                | * Tables
                | * And more — I am running out of random things to say 😆
                |    * But I'm not!
                |       * Have fun indenting your lists as your heart pleases!
                |
                |```kotlin
                |fun hello() = "World"
                |```
                |
                |    Indented code here!
                """
          .trimMargin(),
        Modifier.fillMaxWidth()
          .background(JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor(), RoundedCornerShape(8.dp))
          .border(1.dp, JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toComposeColor(), RoundedCornerShape(8.dp))
          .padding(8.dp),
        enabled = enabled,
        selectable = selectable,
        onUrlClick = { url -> BrowserUtil.open(url) },
      )
    }
  }
}
