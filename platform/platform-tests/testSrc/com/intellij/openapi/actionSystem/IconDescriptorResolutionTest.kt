// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.findIconDescriptor
import com.intellij.openapi.actionSystem.impl.toDescriptorClassName
import com.intellij.ui.icons.iconDescriptorClassName
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.lang.reflect.Modifier
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * How an XML icon reference becomes an [IconDescriptor] (`findIconDescriptor`): resource paths, reflective references to
 * generated descriptors, and the legacy icon's own resource for references without a generated descriptor.
 */
@TestApplication
class IconDescriptorResolutionTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      IntelliJIconManager.activate()
    }
  }

  private val classLoader: ClassLoader = AllIcons::class.java.classLoader

  /**
   * Pins the icon class → descriptor class naming (`toDescriptorClassName`) to what the generator produces: every
   * `AllIcons` icon, a deprecated one too, resolves to the `AllIconDescriptors` entry of the same name.
   */
  @Test
  fun `every AllIcons reference, also a deprecated one, resolves to its generated descriptor`() {
    val references = allIconsReferences(AllIcons::class.java, "AllIcons")
    assertTrue(references.size > 1000, "expected the AllIcons tree, found ${references.size} icons")
    val deprecated = references.filter { (_, holder, field) -> holder.getDeclaredField(field).isAnnotationPresent(java.lang.Deprecated::class.java) }
    assertTrue(deprecated.isNotEmpty(), "expected deprecated AllIcons icons, the generator emits descriptors for them too")
    val unresolved = references.filter { (reference, holder, field) ->
      val expected = generatedDescriptor(holder, field)
      expected == null || findIconDescriptor(reference, classLoader) !== expected
    }
    assertEquals(emptyList(), unresolved.map { it.first }.take(20))
  }

  /**
   * The expected names come from `iconDescriptorClassName`, the rule that the icon class generator uses. The icon class
   * names are the ones that the generator has, `StudioIllustrations` of Android artwork too.
   */
  @Test
  fun `descriptor class names follow the generator`() {
    assertEquals(AllIconDescriptors::class.java.name, toDescriptorClassName(AllIcons::class.java.name))
    assertEquals(AllIconDescriptors.Actions::class.java.name, toDescriptorClassName(AllIcons.Actions::class.java.name))
    for (simpleName in listOf("SomeIcons", "AndroidIcons", "StudioIcons", "StudioIllustrations", "Images")) {
      val expected = "icons.${iconDescriptorClassName(simpleName)}\$Bar\$Baz"
      assertEquals(expected, toDescriptorClassName("icons.$simpleName\$Bar\$Baz"), simpleName)
    }
  }

  @Test
  fun `the generator rule keeps the names of the generated descriptor classes`() {
    assertEquals("AllIconDescriptors", iconDescriptorClassName("AllIcons"))
    assertEquals("AndroidIconDescriptors", iconDescriptorClassName("AndroidIcons"))
    assertEquals("StudioIconDescriptors", iconDescriptorClassName("StudioIcons"))
    assertEquals("StudioIllustrationDescriptors", iconDescriptorClassName("StudioIllustrations"))
  }

  @Test
  fun `a resource path is an image of that resource, with a leading slash removed`() {
    val descriptor = findIconDescriptor("/expui/general/add.svg", classLoader)
    assertNotNull(descriptor)
    assertTrue("path='expui/general/add.svg'" in descriptor.toString(), descriptor.toString())
  }

  @Test
  fun `a reference to a holder without generated descriptors loads the legacy icon's resource`() {
    val descriptor = findIconDescriptor("${LegacyOnlyTestIcons::class.java.name}.Add", classLoader)
    assertNotNull(descriptor)
    assertTrue("path='expui/general/add.svg'" in descriptor.toString(), descriptor.toString())
  }

  @Test
  fun `a reference without a generated getter, like an icon newer than the generated holder, loads the legacy icon's resource`() {
    val current = findIconDescriptor("${PartialTestIcons::class.java.name}.Current", classLoader)
    assertSame(PartialTestIconDescriptors.Current, current)

    val deprecated = findIconDescriptor("${PartialTestIcons::class.java.name}.Old", classLoader)
    assertNotNull(deprecated)
    assertTrue("path='expui/general/remove.svg'" in deprecated.toString(), deprecated.toString())
  }

  @Test
  fun `a reference to a legacy icon that is not an image is bridged as a Swing icon`() {
    for (field in listOf("Empty", "Layered", "Animated")) {
      val descriptor = findIconDescriptor("${LegacyOnlyTestIcons::class.java.name}.$field", classLoader)
      assertNotNull(descriptor, field)
      assertTrue("SwingIconLayer" in descriptor.toString(), "$field: $descriptor")
    }

    // a short-name reference of a badge, which is not a CachedImageIcon
    assertNotNull(findIconDescriptor("Badge.beta", classLoader), "the badge must be bridged")
  }

  @Test
  fun `a legacy icon whose path is relative to its class is bridged, because the class loader root has no such resource`() {
    val descriptor = findIconDescriptor("${LegacyOnlyTestIcons::class.java.name}.ClassRelative", classLoader)
    assertNotNull(descriptor)
    assertTrue("SwingIconLayer" in descriptor.toString(), descriptor.toString())

    // a headless test paints no legacy icon until the icons are activated
    IconLoader.activate()
    try {
      val icon = LegacyOnlyTestIcons.ClassRelative
      assertEquals(16, icon.iconWidth, "the legacy icon must find its resource next to its class")
      assertEquals(pixels(icon), pixels(descriptor), "the bridge must paint the legacy icon")
    }
    finally {
      IconLoader.deactivate()
    }
  }

  @Test
  fun `a legacy image icon that is not plain, for example a scaled one, is bridged, so that the icon keeps its look`() {
    val descriptor = findIconDescriptor("${LegacyOnlyTestIcons::class.java.name}.Scaled", classLoader)
    assertNotNull(descriptor)
    assertTrue("SwingIconLayer" in descriptor.toString(), descriptor.toString())
  }

  @Test
  fun `a patched resource path keeps the original path and loads the resource the patcher redirects to`() {
    val expected = pixels(imageIconDescriptor("expui/general/remove.svg", classLoader))
    val patcher = RedirectingPatcher(mapOf("/test/old.svg" to "/expui/general/remove.svg"))
    IconLoader.installPathPatcher(patcher)
    try {
      val descriptor = assertNotNull(findIconDescriptor("/test/old.svg", classLoader))
      // the image loader patches the path, so that a later theme patches the original path again
      assertTrue("path='test/old.svg'" in descriptor.toString(), descriptor.toString())
      assertEquals(expected, pixels(descriptor), "the redirected resource must be drawn")
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  @Test
  fun `a chained redirect is followed once, like in findIconByPath`() {
    val expected = pixels(imageIconDescriptor("expui/general/add.svg", classLoader))
    val patcher = RedirectingPatcher(mapOf("/test/old.svg" to "/expui/general/add.svg",
                                           "/expui/general/add.svg" to "/expui/general/remove.svg"))
    IconLoader.installPathPatcher(patcher)
    try {
      val descriptor = assertNotNull(findIconDescriptor("/test/old.svg", classLoader))
      assertEquals(expected, pixels(descriptor), "the first redirect must be drawn, not the second one")
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  private fun pixels(descriptor: IconDescriptor): List<Int> = pixels(descriptor.createSwingIcon())

  private fun pixels(icon: Icon): List<Int> {
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) } }
  }

  @Test
  fun `a resource path patched to a reflective reference resolves to its generated descriptor`() {
    val patcher = RedirectingPatcher(mapOf("/test/old.svg" to "AllIcons.Actions.Copy"))
    IconLoader.installPathPatcher(patcher)
    try {
      assertSame(AllIconDescriptors.Actions.Copy, findIconDescriptor("test/old.svg", classLoader))
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  @Test
  fun `a reflective reference is not patched, like in findIconByPath`() {
    val patcher = RedirectingPatcher(mapOf("/AllIcons.Actions.Copy" to "/expui/general/remove.svg"))
    IconLoader.installPathPatcher(patcher)
    try {
      assertSame(AllIconDescriptors.Actions.Copy, findIconDescriptor("AllIcons.Actions.Copy", classLoader))
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  @Test
  fun `an unresolvable reference has no descriptor`() {
    assertNull(findIconDescriptor("AllIcons.Actions.NoSuchIcon", classLoader))
    assertNull(findIconDescriptor("com.example.MissingIcons.Foo", classLoader))
  }

  /** `(reference, holder class, field name)` of every icon in [holder] and its nested holders, also a deprecated one. */
  private fun allIconsReferences(holder: Class<*>, prefix: String): List<Triple<String, Class<*>, String>> {
    val own = holder.declaredFields
      .filter { Modifier.isStatic(it.modifiers) && Icon::class.java.isAssignableFrom(it.type) }
      .map { Triple("$prefix.${it.name}", holder, it.name) }
    return own + holder.declaredClasses.flatMap { allIconsReferences(it, "$prefix.${it.simpleName}") }
  }

  /** The descriptor the generated holder for [holder] has for [field], read independently of `findIconDescriptor`. */
  private fun generatedDescriptor(holder: Class<*>, field: String): IconDescriptor? {
    val descriptorClass = runCatching { AllIconDescriptors::class.java.classLoader.loadClass(toDescriptorClassName(holder.name)) }.getOrNull()
                          ?: return null
    val getter = runCatching { descriptorClass.getMethod("get" + field.replaceFirstChar { it.uppercaseChar() }) }.getOrNull() ?: return null
    return getter.invoke(null) as? IconDescriptor
  }
}

/** A legacy icon holder that has no generated `*IconDescriptors` class. */
@Suppress("unused")
object LegacyOnlyTestIcons {
  @JvmField
  val Add: Icon = IconLoader.getIcon("expui/general/add.svg", AllIcons::class.java.classLoader)

  /** The resource is next to this class, see `testResources`. The class loader root has no such resource. */
  @JvmField
  val ClassRelative: Icon = IconLoader.getIcon("classRelativeTestIcon.svg", LegacyOnlyTestIcons::class.java)

  @JvmField
  val Scaled: Icon = (IconLoader.getIcon("expui/general/add.svg", AllIcons::class.java.classLoader) as CachedImageIcon).scale(2f)

  @JvmField
  val Empty: Icon = EmptyIcon.create(16)

  @JvmField
  val Layered: Icon = LayeredIcon.layeredIcon(arrayOf(AllIcons.General.Add, AllIcons.General.Remove))

  @JvmField
  val Animated: Icon = AnimatedIcon.Default.INSTANCE
}

/** A legacy icon holder whose descriptor holder lacks one icon, for example an icon that is newer than the generated holder. */
@Suppress("unused")
object PartialTestIcons {
  @JvmField
  val Current: Icon = IconLoader.getIcon("expui/general/add.svg", AllIcons::class.java.classLoader)

  @JvmField
  val Old: Icon = IconLoader.getIcon("expui/general/remove.svg", AllIcons::class.java.classLoader)
}

@Suppress("unused")
object PartialTestIconDescriptors {
  @JvmStatic
  val Current: IconDescriptor = com.intellij.platform.icons.imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
}

/** Redirects the paths in [redirects], like `icon-robots.txt` redirects an old icon path. A patcher gets a path with a leading `/`. */
private class RedirectingPatcher(private val redirects: Map<String, String>) : IconPathPatcher() {
  override fun patchPath(path: String, classLoader: ClassLoader?): String? = redirects[path]
}
