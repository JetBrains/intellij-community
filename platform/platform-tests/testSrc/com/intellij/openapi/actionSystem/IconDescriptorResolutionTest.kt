// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.findIconDescriptor
import com.intellij.openapi.actionSystem.impl.toDescriptorClassName
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
   * Pins the icon class → descriptor class naming (`toDescriptorClassName`) to what the generator produces: every current
   * `AllIcons` icon resolves to the `AllIconDescriptors` entry of the same name.
   */
  @Test
  fun `every current AllIcons reference resolves to its generated descriptor`() {
    val references = allIconsReferences(AllIcons::class.java, "AllIcons")
    assertTrue(references.size > 1000, "expected the AllIcons tree, found ${references.size} icons")
    val unresolved = references.filter { (reference, holder, field) ->
      val expected = generatedDescriptor(holder, field)
      expected == null || findIconDescriptor(reference, classLoader) !== expected
    }
    assertEquals(emptyList(), unresolved.map { it.first }.take(20))
  }

  @Test
  fun `descriptor class names follow the generator`() {
    assertEquals("com.intellij.platform.icons.AllIconDescriptors", toDescriptorClassName("com.intellij.icons.AllIcons"))
    assertEquals("com.intellij.platform.icons.AllIconDescriptors\$Actions", toDescriptorClassName("com.intellij.icons.AllIcons\$Actions"))
    assertEquals("icons.SomeIconDescriptors\$Bar\$Baz", toDescriptorClassName("icons.SomeIcons\$Bar\$Baz"))
    assertNull(toDescriptorClassName("com.example.Images"))
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
  fun `a reference without a generated getter, like a deprecated icon, loads the legacy icon's resource`() {
    val current = findIconDescriptor("${PartialTestIcons::class.java.name}.Current", classLoader)
    assertSame(PartialTestIconDescriptors.Current, current)

    val deprecated = findIconDescriptor("${PartialTestIcons::class.java.name}.Old", classLoader)
    assertNotNull(deprecated)
    assertTrue("path='expui/general/remove.svg'" in deprecated.toString(), deprecated.toString())
  }

  @Test
  fun `an unresolvable reference has no descriptor`() {
    assertNull(findIconDescriptor("AllIcons.Actions.NoSuchIcon", classLoader))
    assertNull(findIconDescriptor("com.example.MissingIcons.Foo", classLoader))
  }

  /** `(reference, holder class, field name)` of every current (not deprecated) icon in [holder] and its nested holders. */
  private fun allIconsReferences(holder: Class<*>, prefix: String): List<Triple<String, Class<*>, String>> {
    val own = holder.declaredFields
      .filter { Modifier.isStatic(it.modifiers) && Icon::class.java.isAssignableFrom(it.type) && !it.isAnnotationPresent(java.lang.Deprecated::class.java) }
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
}

/** A legacy icon holder whose descriptor holder lacks one icon, like a deprecated icon the generator leaves out. */
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
  val Current: IconDescriptor = com.intellij.platform.icons.imageResIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
}
