// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

final class UsageFilteringRulesActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    MessageBus messageBus = project.getMessageBus();
    messageBus.connect().subscribe(DynamicPluginListener.TOPIC, new NotifyRulesChangedListener(messageBus));
  }
}

final class NotifyRulesChangedListener implements DynamicPluginListener {

  private final MessageBus myMessageBus;

  NotifyRulesChangedListener(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
  }

  @Override
  public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    myMessageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
  }

  @Override
  public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    myMessageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
  }
}
