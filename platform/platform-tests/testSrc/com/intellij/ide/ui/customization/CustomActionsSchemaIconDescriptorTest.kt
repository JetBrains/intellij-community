// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val sourceDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
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
    val target = action(descriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))

    customize(target, file.toString())

    val descriptor = assertNotNull(target.templatePresentation.iconDescriptor)
    val text = descriptor.toString()
    assertTrue("FileImageResourceLocation(path='${file.invariantSeparatorsPathString}')" in text, "the descriptor must load the file, was: $text")
    assertFalse("SwingIconLayer" in text, "the custom icon must not be bridged from a Swing icon, was: $text")
    assertFalse(target.isDefaultIcon)
  }

  @Test
  fun `a chosen dark variant loads the base file, so that the variant follows the theme`() {
    val base = svg("custom.svg", 16)
    val dark = svg("custom_dark.svg", 16)
    val target = action(descriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))

    customize(target, dark.toString())

    val text = assertNotNull(target.templatePresentation.iconDescriptor).toString()
    assertTrue("FileImageResourceLocation(path='${base.invariantSeparatorsPathString}')" in text, "the base file must be loaded, was: $text")
  }

  @Test
  fun `a chosen dark variant at a file URL with a space is kept when there is no base file`() {
    val folder = Files.createDirectories(dir.resolve("with space"))
    val dark = folder.resolve("custom_dark.svg")
    Files.writeString(dark, """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="#FF0000"/></svg>""")
    val target = action(descriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))
    // as the customization dialog may store it: a file: URL with an unescaped space
    val url = "file:" + FileUtil.toSystemIndependentName(dark.toString())

    customize(target, url)

    val text = assertNotNull(target.templatePresentation.iconDescriptor).toString()
    assertTrue("FileImageResourceLocation(path='${dark.invariantSeparatorsPathString}')" in text, "the chosen dark file must be loaded, was: $text")
  }

  @Test
  fun `a custom icon file that does not exist keeps the originally declared descriptor`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    // e.g. moved or deleted, or a customization synced from another machine
    customize(target, dir.resolve("missing.svg").toString())

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
    assertTrue(target.isDefaultIcon, "the declared icon is shown")
  }

  @Test
  fun `a custom icon at an invalid file path keeps the originally declared descriptor`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    // a NUL character makes the path invalid on every OS
    customize(target, dir.resolve("custom.svg").toString() + "\u0000")

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
    assertTrue(target.isDefaultIcon, "the declared icon is shown")
  }

  @Test
  fun `a custom icon at a malformed file URL keeps the originally declared descriptor`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    customize(target, "file://[malformed/custom.svg")

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
    assertTrue(target.isDefaultIcon, "the declared icon is shown")
  }

  @Test
  fun `a custom icon at a location the icons API does not load keeps the originally declared descriptor`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    customize(target, "https://example.com/icon.svg")

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
  }

  @Test
  fun `a custom icon on a network share keeps the originally declared descriptor`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    // a file: URL with a host, and UNC paths: the file is not read, also not to check that it exists
    // also a UNC path with escaped backslashes, which is `/\\server\share\icon.svg` after the unescape
    for (path in listOf("file://server/share/icon.svg", "file:////server/share/icon.svg", "//server/share/icon.svg",
                        "\\\\server\\share\\icon.svg", "file:/%5C%5Cserver%5Cshare%5Cicon.svg")) {
      val target = action(descriptor = originalDescriptor)

      customize(target, path)

      assertSame(originalDescriptor, target.templatePresentation.iconDescriptor, "$path must not be loaded")
    }
  }

  @Test
  fun `a file URL with a host is not loaded from the local path of that URL`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)
    // the host names another machine: without the check of the host, the local file at the same path is loaded
    val local = svg("custom.svg", 16)

    customize(target, "file://server" + local.toUri().rawPath)

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
  }

  @Test
  fun `a chosen dark variant in a jar is kept, because the base entry cannot be checked`() {
    val target = action(descriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader))

    customize(target, "jar:file:/icons/icons.jar!/custom_dark.svg")

    val text = assertNotNull(target.templatePresentation.iconDescriptor).toString()
    assertTrue("jar:file:/icons/icons.jar!/custom_dark.svg" in text, "the chosen entry must be loaded, was: $text")
  }

  @Test
  fun `removing the customization restores the originally declared descriptor`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)
    val schema = CustomActionsSchema(null)
    schema.addIconCustomization("target", svg("custom.svg", 16).toString())
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })

    schema.removeIconCustomization("target")
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor, "the declared icon is shown again")
    // like `initActionIcon` of the legacy path, which compares the restored icon with the custom icon before the reset
    assertFalse(target.isDefaultIcon)
  }

  @Test
  fun `removing the customization of an action without an icon removes the custom icon`() {
    val target = action(descriptor = null)
    val schema = CustomActionsSchema(null)
    schema.addIconCustomization("target", svg("custom.svg", 16).toString())
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })
    assertNotNull(target.templatePresentation.iconDescriptor, "the custom icon is shown")

    schema.removeIconCustomization("target")
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })

    assertNull(target.templatePresentation.iconDescriptor, "the action has no icon again")
    // like `initActionIcon` of the legacy path, which compares the restored icon with the custom icon before the reset
    assertFalse(target.isDefaultIcon)
  }

  @Test
  fun `without a customization the originally declared descriptor is kept`() {
    val originalDescriptor = imageIconDescriptor("expui/general/copy.svg", AllIcons::class.java.classLoader)
    val target = action(descriptor = originalDescriptor)

    val schema = CustomActionsSchema(null)
    schema.initActionIcon(anAction = target, actionId = "target", actionSupplier = { null })

    assertSame(originalDescriptor, target.templatePresentation.iconDescriptor)
    assertTrue(target.isDefaultIcon, "an action without a customization keeps its default icon")
  }
}
