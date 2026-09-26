// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.ui.icons.isReflectivePath
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.Test
import javax.swing.Icon

class IconLoaderTest {
  @Test
  fun reflectivePathCheck() {
    assertThat(isReflectivePath("AllIcons.Actions.Diff")).isTrue()
    assertThat(isReflectivePath("com.intellij.kubernetes.KubernetesIcons.Kubernetes_Y")).isTrue()
  }

  @Test
  fun reflectivePathNestedClass() {
    assertThat(getReflectiveIcon("com.intellij.ui.TestIcons.TestNestedIcons.ToolWindow",
                                 TestIcons.TestNestedIcons::class.java.classLoader))
      .isEqualTo(TestIcons.TestNestedIcons.ToolWindow)
  }

  @Test
  fun reflectivePathNestedClassWithDollar() {
    assertThat(getReflectiveIcon("com.intellij.ui.TestIcons\$TestNestedIcons.ToolWindow",
                                 TestIcons.TestNestedIcons::class.java.classLoader))
      .isEqualTo(TestIcons.TestNestedIcons.ToolWindow)
  }

  @Test
  fun reflectivePath() {
    assertThat(getReflectiveIcon("com.intellij.ui.TestIcons.NonNested",
                                 TestIcons::class.java.classLoader))
      .isEqualTo(TestIcons.NonNested)
  }

  @Test
  fun reflectivePathAllIcons() {
    assertThat(getReflectiveIcon("AllIcons.FileTypes.AddAny",
                                 TestIcons::class.java.classLoader))
      .isEqualTo(AllIcons.FileTypes.AddAny)
  }

  /**
   * IJPL-244871 SOE: DarkReplacer.replaceIcon
   */
  @Test
  fun recursiveLayeredIcon_setIcon() {
    val layered = LayeredIcon(1)
    assertErrorLogged<Throwable> {
      layered.setIcon(IconUtil.toSize(layered, 16, 16), 0)
    }
  }

  /**
   * IJPL-244871 SOE: DarkReplacer.replaceIcon
   */
  @Test
  fun recursiveLayeredIcon_getDarkIcon() {
    lateinit var layered: LayeredIcon
    layered = LayeredIcon.layeredIcon { arrayOf(layered)}

    assertErrorLogged<Throwable> {
      IconLoader.getDarkIcon(layered, true)
    }
  }
}

internal class TestIcons {
  companion object {
    @JvmField
    val NonNested: Icon = EmptyIcon.create(21)
  }

  class TestNestedIcons {
    companion object {
      @JvmField
      val ToolWindow: Icon = EmptyIcon.create(42)
    }
  }
}