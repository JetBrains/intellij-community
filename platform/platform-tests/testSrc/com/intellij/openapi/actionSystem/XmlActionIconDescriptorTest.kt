// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * End-to-end coverage for wiring XML-declared action icons into the experimental icons pipeline:
 * an `<action icon="...">` string is turned into an [IconDescriptor] on the template presentation, in parallel to the
 * legacy Swing icon. See `ActionManagerXmlSupport.loadIconDescriptor`.
 */
@TestApplication
class XmlActionIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates it, and only when not headless
      IntelliJIconManager.activate()
    }
  }

  /**
   * `$Copy` is declared in intellij.platform.ide.impl.actions.xml as `icon="AllIcons.Actions.Copy"` — a reflective
   * reference. It must resolve to the singleton descriptor from the generated `AllIconDescriptors`, proving both the
   * `AllIcons.` -> `AllIconDescriptors` mapping and the full stub -> action -> presentation wiring.
   */
  @Test
  @RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
  fun `reflective XML icon reference resolves to the generated descriptor`() {
    val action = ActionManager.getInstance().getAction("\$Copy")
    assertNotNull(action, "the \$Copy action must be registered")

    val descriptor = action.templatePresentation.iconDescriptor
    assertNotNull(descriptor, "a descriptor must be derived from the XML icon reference")
    assertSame(AllIconDescriptors.Actions.Copy, descriptor)
  }

  /**
   * The XML loader installs the descriptor supplier unconditionally; the [ExperimentalIcons] gate lives on the read side
   * ([Presentation.getIconDescriptor]), not the write side. So even while the pipeline is off the raw, non-gated supplier
   * ([Presentation.getIconDescriptorSupplier]) is wired from the XML `icon` attribute and resolves to the generated
   * descriptor when evaluated.
   */
  @Test
  @RegistryKey(ExperimentalIcons.REGISTRY_KEY, "false")
  fun `descriptor supplier is wired from XML regardless of the gate`() {
    val action = ActionManager.getInstance().getAction("\$Copy")
    assertNotNull(action, "the \$Copy action must be registered")

    val supplier = action.templatePresentation.iconDescriptorSupplier
    assertNotNull(supplier, "the XML icon must always install a descriptor supplier")
    assertSame(AllIconDescriptors.Actions.Copy, supplier.get())
  }
}
