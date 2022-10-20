// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.ide.util.TipsOrderUtil.SHUFFLE_ALGORITHM;
import static com.intellij.ide.util.TipsOrderUtil.SORTING_ALGORITHM;

public final class TipsOfTheDayUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ui.tips", 12);

  public enum DialogType {automatically, manually}

  public static final EventId NEXT_TIP = GROUP.registerEvent("next.tip");
  public static final EventId PREVIOUS_TIP = GROUP.registerEvent("previous.tip");

  private static final EventId1<DialogType> DIALOG_SHOWN =
    GROUP.registerEvent("dialog.shown", EventFields.Enum("type", DialogType.class));

  private static final EventId2<Boolean, Boolean> DIALOG_CLOSED =
    GROUP.registerEvent("dialog.closed", EventFields.Boolean("keep_showing_before"), EventFields.Boolean("keep_showing_after"));

  private static final StringEventField ALGORITHM_FIELD =
    EventFields.String("algorithm", List.of(SHUFFLE_ALGORITHM, SORTING_ALGORITHM, "unknown"));
  private static final EventId3<String, String, String> TIP_SHOWN =
    GROUP.registerEvent("tip.shown",
                        EventFields.StringValidatedByCustomRule("tip_id", TipInfoValidationRule.class),
                        ALGORITHM_FIELD,
                        EventFields.Version);

  private static final EventId2<String, Long> TIP_PERFORMED =
    GROUP.registerEvent("tip.performed",
                        EventFields.StringValidatedByCustomRule("tip_id", TipInfoValidationRule.class),
                        EventFields.Long("time_passed"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void triggerTipShown(@NotNull TipAndTrickBean tip, @NotNull String algorithm, @Nullable String version) {
    TIP_SHOWN.log(tip.getId(), algorithm, version);
  }

  public static void triggerDialogShown(@NotNull DialogType type) {
    DIALOG_SHOWN.log(type);
  }

  public static void triggerDialogClosed(boolean showOnStartupBefore) {
    DIALOG_CLOSED.log(showOnStartupBefore, GeneralSettings.getInstance().isShowTipsOnStartup());
  }

  public static void triggerTipUsed(@NotNull String tipId, long timePassed) {
    TIP_PERFORMED.log(tipId, timePassed);
  }

  public static class TipInfoValidationRule extends CustomValidationRule {
    public static final String RULE_ID = "tip_info";

    @NotNull
    @Override
    public String getRuleId() {
      return RULE_ID;
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      PluginInfo info = context.getPayload(PLUGIN_INFO);
      if (info != null) {
        return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }

      Object tipId = context.eventData.get("tip_id");
      if (tipId instanceof String) {
        TipAndTrickBean tip = TipAndTrickBean.findById((String)tipId);
        if (tip != null) {
          PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoByDescriptor(tip.getPluginDescriptor());
          context.setPayload(PLUGIN_INFO, pluginInfo);
          return pluginInfo.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }

      return ValidationResultType.REJECTED;
    }
  }
}
