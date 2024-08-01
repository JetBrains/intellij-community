// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

final class UpdateInfoStatsCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ide.update.dialog", 2);
  private static final EventId1<String> CLICK =
    GROUP.registerEvent("link.clicked", EventFields.StringValidatedByCustomRule("url", UrlValidationRule.class));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  static void click(@NotNull String url) {
    CLICK.log(url);
  }

  public static final class UrlValidationRule extends CustomValidationRule {
    private static final String ID = "update_dialog_rule_id";
    private static final List<String> JB_DOMAINS =
      Arrays.asList("jetbrains.com", "intellij.net", "intellij.com", "kotlinlang.org", "jb.gg");

    @Override
    public @NotNull String getRuleId() {
      return ID;
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      try {
        String host = URI.create(data).getHost();
        if (JB_DOMAINS.stream().anyMatch(domain -> host.endsWith(domain))) {
          return ValidationResultType.ACCEPTED;
        }
      }
      catch (Exception ignored) {
      }
      return ValidationResultType.REJECTED;
    }
  }
}
