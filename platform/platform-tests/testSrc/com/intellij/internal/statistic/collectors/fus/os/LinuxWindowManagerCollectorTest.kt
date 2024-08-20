// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class LinuxWindowManagerCollectorTest(private val wmName: String, val reported: String) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data() : Collection<Array<String>> {
      return listOf(
        arrayOf("ubuntu:GNOME", "Ubuntu Gnome"),
        arrayOf("ubuntu_GNOME", "Ubuntu Gnome"),
        arrayOf("ubuntu-communitheme_ubuntu_GNOME", "Ubuntu Gnome"),
        arrayOf("communitheme_ubuntu_GNOME", "Ubuntu Gnome"),
        arrayOf("THEMENAME_ubuntu_GNOME", "Ubuntu Gnome"),

        arrayOf("gnome", "Gnome"),
        arrayOf("GNOME", "Gnome"),
        arrayOf("Gnome", "Gnome"),
        arrayOf("GNOME-Classic_GNOME", "Gnome Classic"),
        arrayOf("Budgie_GNOME", "Budgie Gnome"),
        arrayOf("X-Budgie_GNOME", "Budgie Gnome"),
        arrayOf("GNOME-Flashback_Unity", "GNOME Flashback Unity"),
        arrayOf("GNOME-Flashback:Unity", "GNOME Flashback Unity"),
        arrayOf("GNOME-Flashback_GNOME", "GNOME Flashback Gnome"),
        arrayOf("GNOME-Flashback:GNOME", "GNOME Flashback Gnome"),
        arrayOf("pop_GNOME", "pop_GNOME"),
        arrayOf("Awesome_GNOME", "Awesome_GNOME"),

        arrayOf("X-Cinnamon", "X-Cinnamon"),
        arrayOf("x-cinnamon", "X-Cinnamon"),
        arrayOf("xfce", "XFCE"),
        arrayOf("XFCE", "XFCE"),
        arrayOf("deepin", "Deepin"),
        arrayOf("Deepin", "Deepin"),
        arrayOf("Unity", "Unity"),
        arrayOf("UNITY", "Unity"),
        arrayOf("Pantheon", "Pantheon"),
        arrayOf("PANTHEON", "Pantheon"),
        arrayOf("i3", "i3"),
        arrayOf("KDE", "KDE"),
        arrayOf("LXDE", "LXDE"),
        arrayOf("MATE", "MATE"),

        arrayOf("Unity_Unity7_ubuntu", "Unity7"),
        arrayOf("Unity_Unity7", "Unity7"),

        arrayOf("LXQt", "LXQt"),
        arrayOf("X-LXQt", "LXQt"),

        arrayOf("X-Generic", "X-Generic"),
        arrayOf("ICEWM", "ICEWM"),
        arrayOf("UKUI", "UKUI"),
        arrayOf("Fluxbox", "Fluxbox"),
        arrayOf("LG3D", "LG3D"),
        arrayOf("lg3d", "LG3D"),
        arrayOf("Enlightenment", "Enlightenment"),
        arrayOf("default.desktop", "default.desktop"),

        arrayOf("unknown value", "unknown value"),
        arrayOf("UNKNOWN VALUE", "unknown value")
      )
    }
  }

  @Test
  fun `test linux vm parser`() {
    assertEquals(reported, LinuxWindowManagerUsageCollector.toReportedName(wmName))
  }
}