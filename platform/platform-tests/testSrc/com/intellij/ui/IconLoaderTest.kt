// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.ui.icons.isReflectivePath
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.Test
import java.awt.GraphicsEnvironment
import java.net.URLClassLoader
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

  /**
   * IJPL-252580 the disabled icon cache keeps an icon of an unloaded plugin
   */
  @Test
  fun detachClassLoaderClearsDisabledIconOfThatLoader() {
    withIconOfOwnClassLoader { classLoader, icon ->
      val disabledIcon = IconLoader.getDisabledIcon(icon)
      assertThat(IconLoader.getDisabledIcon(icon)).isSameAs(disabledIcon)

      IconLoader.detachClassLoader(classLoader)

      assertThat(IconLoader.getDisabledIcon(icon)).isNotSameAs(disabledIcon)
    }
  }

  /**
   * IJPL-252580 a path transform change after the detach must not re-attach the class loader of the icon
   */
  @Test
  fun detachedIconStaysDetachedAfterTransformChange() {
    withIconOfOwnClassLoader { classLoader, icon ->
      assertThat(icon.url).isNotNull()

      IconLoader.detachClassLoader(classLoader)
      assertThat(icon.url).isNull()
      assertThat(icon.originalPath).isEqualTo(TEST_ICON_PATH)

      IconLoader.clearCache()
      assertThat(icon.url).isNull()
      assertThat(icon.originalPath).isEqualTo(TEST_ICON_PATH)
    }
  }

  /**
   * Runs [body] with a platform icon that a child class loader of the test loaded, and with the icon loader on.
   * The child class loader stands for a plugin class loader.
   * After [body], the icon loader goes back off in a headless JVM, as it starts there.
   */
  private fun withIconOfOwnClassLoader(body: (ClassLoader, CachedImageIcon) -> Unit) {
    URLClassLoader(emptyArray(), IconLoaderTest::class.java.classLoader).use { classLoader ->
      IconLoader.activate()
      try {
        val icon = IconLoader.getIcon("/$TEST_ICON_PATH", classLoader)
        assertThat(icon).isInstanceOf(CachedImageIcon::class.java)
        body(classLoader, icon as CachedImageIcon)
      }
      finally {
        if (GraphicsEnvironment.isHeadless()) {
          IconLoader.deactivate()
        }
      }
    }
  }
}

private const val TEST_ICON_PATH = "general/add.svg"

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