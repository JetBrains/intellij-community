// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalIconsApi::class)

package com.intellij.devkit.compose.showcase

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.findFile
import com.intellij.ui.BadgeDotProvider
import com.intellij.ui.BadgeIcon
import com.intellij.ui.SpinningProgressIcon
import com.intellij.util.IconUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconManager
import org.jetbrains.icons.design.Circle
import org.jetbrains.icons.design.IconAlign
import org.jetbrains.icons.design.percent
import org.jetbrains.icons.dynamicIcon
import org.jetbrains.icons.icon
import org.jetbrains.icons.legacyIconSupport.swingIcon
import org.jetbrains.icons.legacyIconSupport.toNewIcon
import org.jetbrains.icons.legacyIconSupport.toSwingIcon
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.modifiers.align
import org.jetbrains.icons.modifiers.fillMaxSize
import org.jetbrains.icons.modifiers.patchSvg
import org.jetbrains.icons.modifiers.size
import org.jetbrains.jewel.bridge.toAwtColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.badge
import org.jetbrains.jewel.ui.icon.tintColor
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun Icons(project: Project) {
  val icons = mutableListOf<ShowcaseIcon>()
  val missingIcon = remember { AllIcons.General.Error.toNewIcon(modifier = IconModifier.fillMaxSize()) }

  val duration = 50L
  val animatedIcon = remember {
    icon {
      animation {
        frame(duration) {
          swingIcon(AllIcons.Process.Step_1)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_2)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_3)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_4)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_5)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_6)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_7)
        }
        frame(duration) {
          swingIcon(AllIcons.Process.Step_8)
        }
      }
    }
  }

  icons.add(remember {
    ShowcaseIcon(
      icon {
        icon(missingIcon)
        badge(Color.Green, Circle)
      },
      BadgeIcon(AllIcons.General.Error, Color.Green.toAwtColor(), BadgeDotProvider())
    )
  })

  icons.add(remember {
    ShowcaseIcon(
      icon {
        column(spacing = 5.percent, modifier = IconModifier.fillMaxSize()) {
          row(spacing = 5.percent) {
            icon(missingIcon, modifier = IconModifier.tintColor(Color.Yellow, BlendMode.Color))
            icon(missingIcon, modifier = IconModifier.tintColor(Color.Green, BlendMode.Hue))
          }
          row(spacing = 5.percent)  {
            icon(missingIcon, modifier = IconModifier.tintColor(Color.Blue, BlendMode.Saturation))
            icon(missingIcon, modifier = IconModifier.tintColor(Color.Cyan, BlendMode.Multiply))
          }
        }
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.TopLeft))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.TopCenter))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.TopRight))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.CenterLeft))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).tintColor(Color.Red).align(IconAlign.Center))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.CenterRight))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.BottomLeft))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.BottomCenter))
        icon(animatedIcon, modifier = IconModifier.size(20.percent).align(IconAlign.BottomRight))
      },
      null
    )
  })

  val deferredIcon = remember {
    project.guessProjectDir()?.findFile("src/main/kotlin/com/example/demo/test.kt")?.let {
      IconUtil.getIcon(it, 0, project)
    }
  } ?: AllIcons.General.Error

  icons.add(
    ShowcaseIcon(
      deferredIcon.toNewIcon(),
      deferredIcon
    )
  )

  icons.add(remember {
    ShowcaseIcon(
      animatedIcon,
      SpinningProgressIcon()
    )
  })

  val json = Json { serializersModule = IconManager.getInstance().getSerializersModule() }
  val dynIcon = dynamicIcon(missingIcon)
  val serialized = json.encodeToString(dynIcon)
  val deserialized = json.decodeFromString<Icon>(serialized)
  val greenIcon = icon {
    icon(missingIcon, modifier = IconModifier.patchSvg {
      replaceUnlessMatches("fill", "white", "green")
    })
  }
  val scope = rememberCoroutineScope()
  scope.launch {
    delay(5000)
    dynIcon.swap(greenIcon)
  }

  icons.add(remember {
    ShowcaseIcon(
      deserialized,
      null
    )
  })

  Column(Modifier.fillMaxWidth()) {
    // Header
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
      Text("Name", Modifier.weight(1f))
      Text("Role", Modifier.weight(1f))
    }

    // Body
    Column(Modifier.fillMaxWidth()) {
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("a", Modifier.weight(1f))
        Text("b", Modifier.weight(1f))
      }
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("a", Modifier.weight(1f))
        Text("b", Modifier.weight(1f))
      }
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("a", Modifier.weight(1f))
        Text("b", Modifier.weight(1f))
      }
    }
  }
  IconsShowcase(icons)
}

class ShowcaseIcon(
  val icon: Icon,
  val swingAlternative: javax.swing.Icon?,
)

@Composable
private fun IconsShowcase(icons: List<ShowcaseIcon>) {
  Text("Compose/Swing new/Swing old")
  Row(modifier = Modifier.fillMaxWidth().height(300.dp)) {
    Column(modifier = Modifier.requiredWidth(60.dp)) {
      for (icon in icons) {
        Icon(icon.icon, "Icon")
      }
    }
    wrapStaticSwingIcons(
      icons.map {
        it.icon.toSwingIcon()
      }
    )
    wrapStaticSwingIcons(
      icons.map { it.swingAlternative ?: AllIcons.General.Warning }
    )
  }
}

@Composable
private fun wrapStaticSwingIcons(icons: List<javax.swing.Icon>) {
  SwingPanel(
    factory = {
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        for (icon in icons) {
          add(
            JLabel(
              icon
            ).apply {
              border = null
              isOpaque = false
            }
          )
        }
      }
    },
    background = Color.Transparent,
    modifier = Modifier.width(60.dp).fillMaxHeight(),
  )
}