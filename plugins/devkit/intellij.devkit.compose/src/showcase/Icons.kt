// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.deferredIcon
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.badge
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.icon
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.patchSvg
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.swingIcon
import com.intellij.platform.icons.swing.toAwtColor
import com.intellij.platform.icons.swing.toNewIcon
import com.intellij.platform.icons.swing.toSwingIcon
import com.intellij.ui.BadgeDotProvider
import com.intellij.ui.BadgeIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.SpinningProgressIcon
import com.intellij.util.IconUtil
import kotlinx.coroutines.delay
import org.jetbrains.jewel.bridge.toAwtColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.badge
import org.jetbrains.jewel.ui.icon.circle
import org.jetbrains.jewel.ui.icon.fitArea
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun Icons() {
  val icons = mutableListOf<ShowcaseIcon>()
  val missingIcon = remember {
    AllIcons.General.Error.toNewIcon()
  }
  val warningIcon = remember {
    AllIcons.General.Warning.toNewIcon()
  }
  val settingsIcon = remember {
    AllIcons.General.Settings.toNewIcon()
  }

  val transition = rememberInfiniteTransition()
  val iconScale by transition.animateFloat(
    16f,
    64f,
    infiniteRepeatable(tween(durationMillis = 1000, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
  )

  icons.add(ShowcaseIcon(
    remember {
      icon {
        swingIcon(AllIcons.Actions.NewFolder)
      }
    },
    null,
    "Animated Scale Icon",
    scale = fitArea(iconScale.dp, iconScale.dp),
    allocatedSize = 64.dp
  ))

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
        icon(AllIcons.General.Web.toNewIcon())
      },
      RowIcon(IconUtil.scale(AllIcons.General.Web, null, 2f)),
      "Scaled Icon",
      scale = factor(2f)
    )
  })

  val blueColor = sRGB("#6089EF")

  icons.add(remember {
    ShowcaseIcon(
      icon {
        icon(settingsIcon)
        badge(blueColor, modifier = IconModifier.align(IconAlign.TopRight))
      },
      BadgeIcon(settingsIcon.toSwingIcon(), blueColor.toAwtColor(), BadgeDotProvider()),
      "Icon with Badge"
    )
  })

  icons.add(remember {
    ShowcaseIcon(
      icon {
        icon(settingsIcon)
        badge(blueColor, modifier = IconModifier.align(IconAlign.TopRight))
      },
      BadgeIcon(settingsIcon.toSwingIcon(), blueColor.toAwtColor(), BadgeDotProvider()).scale(10f),
      "Icon with Badge 10x",
      factor(10f)
    )
  })

  icons.add(remember {
    ShowcaseIcon(
      icon {
        icon(missingIcon)
        badge(Color.Green, circle(2.3.dp))
      },
      BadgeIcon(AllIcons.General.Error, Color.Green.toAwtColor(), BadgeDotProvider()),
      "Icon with Badge"
    )
  })

  icons.add(remember {
    ShowcaseIcon(
      icon {
        row {
          icon(missingIcon)
          icon(warningIcon)
        }
      },
      RowIcon(missingIcon.toSwingIcon(), warningIcon.toSwingIcon()),
      "Row Icon"
    )
  })

  icons.add(remember {
    ShowcaseIcon(
      icon {
        column {
          icon(missingIcon)
          icon(warningIcon)
        }
      },
      null,
      "Column Icon"
    )
  })

  icons.add(remember {
    ShowcaseIcon(
      animatedIcon,
      SpinningProgressIcon(),
      "Animated Icon"
    )
  })

  val dynIcon = remember {
    deferredIcon(missingIcon) {
      delay(5000.milliseconds)
      icon {
        icon(missingIcon, modifier = IconModifier.patchSvg {
          replaceUnlessMatches("fill", "white", "green")
        })
      }
    }
  }

  icons.add(remember {
    ShowcaseIcon(
      dynIcon,
      null,
      "Deferred Icon"
    )
  })

  Column(Modifier.fillMaxWidth()) {
    // Header
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
      Text("Title", Modifier.weight(1f))
      Text("Compose", Modifier.weight(1f))
      Text("Swing", Modifier.weight(1f))
      Text("Old Api - Swing", Modifier.weight(1f))
    }

    // Body
    Column(Modifier.fillMaxWidth()) {
      for (icon in icons) {
        var rowModifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        if (icon.allocatedSize != null) {
          rowModifier = rowModifier.size(icon.allocatedSize)
        }
        Row(rowModifier) {
          Text(icon.title, Modifier.weight(1f))
          Column(Modifier.weight(1f)) {
            Icon(icon.icon, "Icon", scale = icon.scale)
          }
          wrapStaticSwingIcon(
            icon.icon.toSwingIcon(icon.scale),
            modifier = Modifier.weight(1f)
          )
          wrapStaticSwingIcon(
            icon.swingAlternative ?: AllIcons.General.Warning,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

class ShowcaseIcon(
  val icon: Icon,
  val swingAlternative: javax.swing.Icon?,
  val title: String,
  val scale: IconScale = factor(1f),
  val allocatedSize: Dp? = null
)

@Composable
private fun wrapStaticSwingIcon(icon: javax.swing.Icon, modifier: Modifier = Modifier) {
  SwingPanel(
    factory = {
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(
          JLabel(
            icon
          ).apply {
            border = null
            isOpaque = false
          }
        )
      }
    },
    background = Color.Transparent,
    modifier = modifier.fillMaxHeight(),
  )
}