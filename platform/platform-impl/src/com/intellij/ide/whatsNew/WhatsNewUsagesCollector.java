// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class WhatsNewUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ide.whats.new", 1);

  static final EventId1<Boolean> PAGE_DISPLAYED = GROUP.registerEvent("page.displayed", EventFields.Boolean("welcome_screen_project"));
  static final EventId1<Boolean> AIR_PROMO = GROUP.registerEvent("air.promo.clicked", EventFields.Boolean("welcome_screen_project"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void pageDisplayed(@NotNull Project project) {
    PAGE_DISPLAYED.log(WelcomeScreenProjectProvider.isWelcomeScreenProject(project));
  }

  public static void airPromoButtonClicked(@NotNull Project project) {
    AIR_PROMO.log(WelcomeScreenProjectProvider.isWelcomeScreenProject(project));
  }
}
