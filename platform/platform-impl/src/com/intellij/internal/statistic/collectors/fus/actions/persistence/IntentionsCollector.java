// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class IntentionsCollector {

  public void record(@NotNull IntentionAction action, @NotNull Language language) {
    record(null, action, language);
  }

  public void record(@Nullable Project project, @NotNull IntentionAction action, @NotNull Language language) {
    final Class<?> clazz = getOriginalHandlerClass(action);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);

    final FeatureUsageData data = new FeatureUsageData().
      addData("id", clazz.getName()).
      addPluginInfo(info).
      addLanguage(language);
    FUCounterUsageLogger.getInstance().logEvent(project, "intentions", "called", data);
  }

  @NotNull
  private static Class<?> getOriginalHandlerClass(@NotNull IntentionAction action) {
    Object handler = action;
    if (action instanceof IntentionActionDelegate) {
      IntentionAction delegate = ((IntentionActionDelegate)action).getDelegate();
      if (delegate != action) {
        return getOriginalHandlerClass(delegate);
      }
    }
    else if (action instanceof QuickFixWrapper) {
      LocalQuickFix fix = ((QuickFixWrapper)action).getFix();
      if (fix != action) {
        handler = fix;
      }
    }
    return handler.getClass();
  }

  public static IntentionsCollector getInstance() {
    return ServiceManager.getService(IntentionsCollector.class);
  }
}

