// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

private const val PREFIX = "ActionsBuiltInAllowedlistTest."

@TestApplication
internal class ActionsBuiltInAllowedlistTest {
  @Test
  fun `the actions of alternating plugins follow the reporting rule of their own plugin`() {
    val jetBrainsPlugin = createBundledPlugin(id = "${PREFIX}jetBrains", vendor = "JetBrains")
    val otherPlugin = createBundledPlugin(id = "${PREFIX}other", vendor = "Some Vendor")

    ActionsCollectorImpl.onActionLoadedFromXml(actionId = "${PREFIX}jetBrains.first", plugin = jetBrainsPlugin)
    ActionsCollectorImpl.onActionLoadedFromXml(actionId = "${PREFIX}jetBrains.second", plugin = jetBrainsPlugin)
    ActionsCollectorImpl.onActionLoadedFromXml(actionId = "${PREFIX}other", plugin = otherPlugin)
    ActionsCollectorImpl.onActionLoadedFromXml(actionId = "${PREFIX}jetBrains.third", plugin = jetBrainsPlugin)
    ActionsCollectorImpl.onActionLoadedFromXml(actionId = "${PREFIX}noPlugin", plugin = null)

    assertThat(ActionsCollectorImpl.canReportActionId("${PREFIX}jetBrains.first")).isTrue()
    assertThat(ActionsCollectorImpl.canReportActionId("${PREFIX}jetBrains.second")).isTrue()
    assertThat(ActionsCollectorImpl.canReportActionId("${PREFIX}other")).isFalse()
    assertThat(ActionsCollectorImpl.canReportActionId("${PREFIX}jetBrains.third")).isTrue()
    assertThat(ActionsCollectorImpl.canReportActionId("${PREFIX}noPlugin")).isFalse()
  }

  private fun createBundledPlugin(id: String, vendor: String): IdeaPluginDescriptorImpl {
    val builder = PluginDescriptorBuilder.builder()
    builder.id = id
    builder.vendor = vendor
    return PluginMainDescriptor(raw = builder.build(), pluginPath = Path.of(id), isBundled = true)
  }
}
