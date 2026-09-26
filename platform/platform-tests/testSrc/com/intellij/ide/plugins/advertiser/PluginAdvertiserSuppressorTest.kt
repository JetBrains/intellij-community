// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserSuppressor
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.isAdvertisementSuppressed
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.ProjectRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginAdvertiserSuppressorTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()

    private val EP_NAME = ExtensionPointName<PluginAdvertiserSuppressor>("com.intellij.pluginAdvertiserSuppressor")
  }

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @Test
  fun notSuppressedWithoutSuppressors() {
    assertFalse(isAdvertisementSuppressed(projectRule.project, LightVirtualFile("foo.lua")))
  }

  @Test
  fun suppressedForMatchingFileOnly() {
    EP_NAME.point.registerExtension(object : PluginAdvertiserSuppressor {
      override fun isSuppressedFor(project: Project, file: VirtualFile): Boolean = file.name.endsWith(".lua")
    }, disposableRule.disposable)

    assertTrue(isAdvertisementSuppressed(projectRule.project, LightVirtualFile("foo.lua")))
    assertFalse(isAdvertisementSuppressed(projectRule.project, LightVirtualFile("foo.txt")))
  }
}
