// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageResIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.scale.JBUIScale
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Coverage for keeping the experimental icons pipeline in sync with user icon customizations, see
 * `CustomActionsSchema.initActionIconDescriptor`. A customization that reuses another action's icon takes that action's
 * descriptor, and a custom icon file is loaded by the icons API from that file, with no Swing icon loaded; resetting the customization restores
 * the originally declared descriptor.
 */
@TestApplication
@RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
class CustomActionsSchemaIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // Swing icons stay deactivated: under the experimental icons pipeline a custom icon is loaded by the icons API only
      IntelliJIconManager.activate()
    }
  }

  @TempDir
  lateinit var dir: Path

  private fun action(descriptor: IconDescriptor? = null): AnAction {
    val anAction = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    if (descriptor != null) anAction.templatePresentation.iconDescriptor = descriptor
    return anAction
  }

  private fun svg(name: String, size: Int): Path {
    val file = dir.resolve(name)
    Files.writeString(file, """<svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size"><rect width="$size" height="$size" fill="#FF0000"/></svg>""")
    return file
  }

  private fun customize(target: AnAction, iconPath: String, actionSupplier: (String) -> AnAction? = { null }) {
    val schema = CustomActionsSchema(null)
    schema.addIconCustomization("target", iconPath)
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = actionSupplier)
  }

  @Test
  fun `reusing another action's icon takes its descriptor and the original is remembered`() {
    val originalDescriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val sourceDescriptor = imageResIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)
    // the icon customization reuses another action's icon (the "reuse from" form of a customization value)
    val source = action(descriptor = sourceDescriptor)

    customize(target, "source") { if (it == "source") source else null }

    assertSame(sourceDescriptor, target.templatePresentation.iconDescriptor, "the source action's descriptor must be reused as it is")
    assertSame(originalDescriptor, target.templatePresentation.getClientProperty(CustomActionsSchema.ORIGINAL_ICON_DESCRIPTOR_KEY),
               "the originally declared descriptor must be preserved for a later reset")
    assertFalse(target.isDefaultIcon)
  }

  @Test
  fun `a custom icon file is loaded by the icons API from that file`() {
    val file = svg("custom.svg", 16)
    val target = action(descriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))

    customize(target, file.toString())

    val descriptor = assertNotNull(target.templatePresentation.iconDescriptor)
    val text = descriptor.toString()
    assertTrue("UrlImageResourceLocation(url='$file')" in text, "the descriptor must load the file, was: $text")
    assertFalse("SwingIconLayer" in text, "the custom icon must not be bridged from a Swing icon, was: $text")
    assertFalse(target.isDefaultIcon)
  }

  @Test
  fun `a custom icon larger than 18x18 is fitted into it, a smaller one keeps its size`() {
    // like the legacy doLoadCustomIcon: users may choose an image of any size
    fun customIcon(size: Int): Icon {
      val target = action(descriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))
      customize(target, svg("custom$size.svg", size).toString())
      return assertNotNull(target.templatePresentation.icon)
    }

    val large = customIcon(36)
    assertEquals(JBUIScale.scale(18), large.iconWidth)
    assertEquals(JBUIScale.scale(18), large.iconHeight)

    val small = customIcon(16)
    assertEquals(JBUIScale.scale(16), small.iconWidth)
    assertEquals(JBUIScale.scale(16), small.iconHeight)

    // a look that enlarges icons (the main toolbar's 16 → 20) scales the fitted result
    assertEquals(JBUIScale.scale(20), (small as ScalableIcon).scale(1.25f).iconWidth)
    val largeScaled = (large as ScalableIcon).scale(1.25f).iconWidth
    assertTrue(largeScaled in JBUIScale.scale(22)..JBUIScale.scale(23), "18 × 1.25 expected, was $largeScaled")
  }

  @Test
  fun `a chosen dark variant loads the base file, so that the variant follows the theme`() {
    val base = svg("custom.svg", 16)
    val dark = svg("custom_dark.svg", 16)
    val target = action(descriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))

    customize(target, dark.toString())

    val text = assertNotNull(target.templatePresentation.iconDescriptor).toString()
    assertTrue("UrlImageResourceLocation(url='$base')" in text, "the base file must be loaded, was: $text")
  }

  @Test
  fun `a chosen dark variant at a file URL with a space is kept when there is no base file`() {
    val folder = Files.createDirectories(dir.resolve("with space"))
    val dark = folder.resolve("custom_dark.svg")
    Files.writeString(dark, """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="#FF0000"/></svg>""")
    val target = action(descriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))
    // as the customization dialog may store it: a file: URL with an unescaped space
    val url = "file:" + FileUtil.toSystemIndependentName(dark.toString())

    customize(target, url)

    val text = assertNotNull(target.templatePresentation.iconDescriptor).toString()
    assertTrue("UrlImageResourceLocation(url='$url')" in text, "the chosen dark file must be loaded, was: $text")
  }

  @Test
  fun `a custom icon file that does not exist keeps the originally declared descriptor`() {
    val originalDescriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    // e.g. moved or deleted, or a customization synced from another machine
    customize(target, dir.resolve("missing.svg").toString())

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
    assertTrue(target.isDefaultIcon, "the declared icon is shown")
  }

  @Test
  fun `a custom icon at a location the icons API does not load keeps the originally declared descriptor`() {
    val originalDescriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    customize(target, "https://example.com/icon.svg")

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
  }

  @Test
  fun `without a customization the originally declared descriptor is kept`() {
    val originalDescriptor = imageResIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    val schema = CustomActionsSchema(null)
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
    assertTrue(target.isDefaultIcon, "an action without a customization keeps its default icon")
  }
}
