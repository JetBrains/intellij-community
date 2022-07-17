// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.util.messages.MessageBus

internal class UsageFilteringRulesActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    val messageBus = project.messageBus
    messageBus.simpleConnect().subscribe(DynamicPluginListener.TOPIC, NotifyRulesChangedListener(messageBus))
  }
}

private class NotifyRulesChangedListener(private val messageBus: MessageBus) : DynamicPluginListener {
  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    messageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run()
  }

  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    messageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run()
  }
}