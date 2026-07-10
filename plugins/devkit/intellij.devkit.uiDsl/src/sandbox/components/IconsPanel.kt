// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.platform.icons.deferredIcon
import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.badge
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.design.rectangle
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.imageIcon
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.margin
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.modifiers.tintColor
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.swingIcon
import com.intellij.platform.icons.swing.toNewIcon
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.IconDeferrer
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.delay
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class IconsPanel : UISandboxPanel {

  override val title: String = "Icons"

  override fun createContent(disposable: Disposable): JComponent {
    val sampleIcon = imageIcon("expui/fileTypes/actionScript.svg", AllIcons::class.java.classLoader)
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.basic.icons")) {
        row {
          icon(sampleIcon)
          for (scale in listOf(
            fitArea(20.dp, 20.dp),
            fitArea(40.dp, 40.dp),
            factor(5.0),
          )) {
            icon(sampleIcon, scale)
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.animated.icon")) {
        row {
          icon {
            animation {
              frame(1000) {
                swingIcon(AllIcons.General.Add)
              }
              frame(1000) {
                swingIcon(AllIcons.General.Remove)
              }
              frame(1000) {
                swingIcon(AllIcons.General.ChevronRight)
              }
              frame(1000) {
                swingIcon(AllIcons.General.ChevronLeft)
              }
            }
          }
          icon {
            animation {
              frame(fadeIn = 500, stay = 500, fadeOut = 500) {
                swingIcon(AllIcons.General.Add)
              }
              frame(fadeIn = 500, stay = 500, fadeOut = 500) {
                swingIcon(AllIcons.General.Remove)
              }
              frame(fadeIn = 500, stay = 500, fadeOut = 500) {
                swingIcon(AllIcons.General.ArrowUp)
              }
              frame(fadeIn = 500, stay = 500, fadeOut = 500) {
                swingIcon(AllIcons.General.ArrowDown)
              }
            }
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.deferred.icon")) {
        row {
          icon(
            deferredIcon(AllIcons.General.GearPlain.toNewIcon()) {
              delay(5.seconds)
              AllIcons.FileTypes.Image.toNewIcon()
            }
          )
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.color.filters")) {
        row {
          icon {
            icon(AllIcons.FileTypes.Image.toNewIcon(), IconModifier.tintColor(Color.White, BlendMode.Saturation))
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.badges")) {
        row {
          icon {
            icon(AllIcons.General.Settings.toNewIcon())
            badge(sRGB(0.2f, 1f, 0.2f, 1f))
          }
          icon {
            icon(AllIcons.General.Settings.toNewIcon())
            badge(sRGB(1f, 0.2f, 0.2f, 1f), rectangle(5.dp, 5.dp))
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.icons.with.layout")) {
        row {
          icon {
            icon(AllIcons.General.Settings.toNewIcon())
            icon(
              AllIcons.FileTypes.Image.toNewIcon(),
              IconModifier
                .align(IconAlign.TopRight)
                .scale(fitArea(10.dp, 10.dp))
            )
          }
          icon {
            row {
              icon(AllIcons.General.Settings.toNewIcon(), IconModifier.margin(right = 2.dp))
              icon(AllIcons.FileTypes.Image.toNewIcon())
            }
          }
          icon {
            column {
              icon(AllIcons.General.Settings.toNewIcon(), IconModifier.margin(bottom = 2.dp))
              icon(AllIcons.FileTypes.Image.toNewIcon())
            }
          }
        }
      }

      var i = 0
      fun createDeferredIcon(): Icon {
        return IconDeferrer.getInstance().deferAsync(AllIcons.FileTypes.Unknown, i++) {
            delay(5000.milliseconds)
            AllIcons.FileTypes.Bazel
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.legacy.icon.support")) {
        lateinit var deferredIconLabel: JLabel
        row {
          icon(
            AllIcons.General.GearPlain.toNewIcon()
          )
          icon(
            AnimatedIcon.Default().toNewIcon()
          )
          deferredIconLabel = icon(
            createDeferredIcon()
          ).component
        }
        row {
          button(DevkitUiDslBundle.message("sandbox.button.recreate.deferred.icon")) {
            deferredIconLabel.icon = createDeferredIcon()
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.legacy.icon.tests")) {
        row {
          icon(
            AnimatedIcon.Blinking(AllIcons.General.Remove)
          )
          icon(
            AnimatedIcon.Fading(AllIcons.General.Add)
          )
        }
      }
    }
  }
}