// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.*;
import com.intellij.internal.statistic.eventLog.validator.storage.IntellijValidationRulesStorage;
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesStorageProvider;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil;
import com.intellij.internal.statistic.utils.StatisticsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>
 *   The data from all collectors is validated before it's recorded locally.
 *   It's necessary to make sure that the data is correct and it doesn't contain personal or proprietary information.<br/>
 *   Validation is performed right before logging in {@link IntellijSensitiveDataValidator#validate}.<br/>
 * </p>
 *
 * <p>
 *   Therefore, each collector should define data scheme and rules which will be used in validation.<br/>
 *   Rules are stored in a separate repository, IDE loads rules from the server during runtime.<br/>
 *   If you use new FUS API (docs: fus-collectors.md) and group is implemented in a platform or bundled plugin,
 *   synchronization between statistics metadata repository and source code is performed semi-automatically.
 *   In other cases or when you need to change group scheme without changing the code, create an <a href="https://youtrack.jetbrains.com/issues/FUS">issue</a>.
 * </p>
 *
 * <p>
 * There are 3 types of rules:
 * <ol>
 *     <li>
 *       <b>Enum</b>: a list of possible values, e.g.
 *       <i>"{enum:started|finished}"</i> checks that the value is equal to 'started' or 'finished'.<br/>
 *      See: {@link EnumValidationRule}
 *     </li>
 *     <li>
 *       <b>Regexp</b>: e.g. <i>"{regexp#integer}</i> checks that the value is integer.<br/>
 *       See: {@link RegexpValidationRule}
 *     </li>
 *     <li>
 *       <b>Custom rule</b>: class which inherits {@link CustomValidationRule} and validates dynamic data like action id or file type, e.g.
 *       <i>"{util#class_name}"</i> checks that the value is a class name from platform, JB plugin or a plugin from JB plugin repository.<br/>
 *       See: {@link com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator}
 *     </li>
 * </ol>
 * </p>
 *
 * <p>
 *   There is also a list of common event data fields which doesn't require validation
 *   because they are always validated in {@link FeatureUsageData}, e.g. "plugin", "lang", etc.
 * </p>
 *
 * <p>Example:</p>
 * <ul>
 * <li><i>"actions"</i> collector records invoked actions
 * ({@link com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl}).<br/>
 *
 * It is validated by the following rules:
 * <pre>
 * {
 *   "event_id" : [ "{enum:action.invoked|custom.action.invoked}" ],
 *   "event_data" : {
 *     "action_id" : [ "{util#action}" ],
 *     "class" : [ "{util#class_name}" ],
 *     "context_menu" : [ "{enum#boolean}" ],
 *     "current_file" : [ "{util#current_file}" ],
 *     "input_event" : [ "{util#shortcut}" ],
 *     "place" : [ "{util#place}" ],
 *     "plugin" : [ "{util#plugin}" ],
 *     "plugin_type" : [ "{util#plugin_type}" ]
 *   }
 * }
 * </pre></li>
 *
 * <li><i>"file.types"</i> collector records information about project files
 * ({@link com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector}).<br/>
 *
 * It is validated by the following rules:
 * <pre>
 * {
 *   "event_id" : ["{enum:file.in.project}" ],
 *   "event_data" : {
 *     "file_type" : [ "{util#file_type}" ]
 *   }
 * }
 * </pre></li>
 * </ul>
 */
public class IntellijSensitiveDataValidator extends SensitiveDataValidator<IntellijValidationRulesStorage> {
  private static final ConcurrentMap<String, IntellijSensitiveDataValidator> ourInstances = new ConcurrentHashMap<>();

  static {
    CustomValidationRule.EP_NAME.addChangeListener(ourInstances::clear, null);
  }

  public static @NotNull IntellijSensitiveDataValidator getInstance(@NotNull String recorderId) {
    return ourInstances.computeIfAbsent(
      recorderId,
      id -> {
        IntellijValidationRulesStorage storage = ValidationRulesStorageProvider.newStorage(recorderId);
        return ApplicationManager.getApplication().isUnitTestMode()
               ? new BlindSensitiveDataValidator(storage, recorderId)
               : new IntellijSensitiveDataValidator(storage, recorderId);
      }
    );
  }

  public static @Nullable IntellijSensitiveDataValidator getIfInitialized(@NotNull String recorderId) {
    return ourInstances.get(recorderId);
  }

  private final String myRecorderId;

  protected IntellijSensitiveDataValidator(@NotNull IntellijValidationRulesStorage storage, @NotNull String recorderId) {
    super(storage);
    myRecorderId = recorderId;
  }

  public boolean isGroupAllowed(@NotNull EventLogGroup group) {
    if (StatisticsRecorderUtil.isTestModeEnabled(myRecorderId)) return true;

    IntellijValidationRulesStorage storage = getValidationRulesStorage();
    if (storage.isUnreachable()) return true;
    return storage.getGroupRules(group.getId()) != null;
  }

  @Override
  public @NotNull Map<String, Object> guaranteeCorrectEventData(@NotNull EventContext context, EventGroupRules groupRules) {
    if (isTestModeEnabled(groupRules)) {
      return context.eventData;
    }

    Map<String, Object> validatedData = super.guaranteeCorrectEventData(context, groupRules);

    boolean containsPluginInfo = validatedData.containsKey("plugin") ||
                                 validatedData.containsKey("plugin_type") ||
                                 validatedData.containsKey("plugin_version");
    PluginInfo pluginInfo = context.getPayload(CustomValidationRule.PLUGIN_INFO);
    if (pluginInfo != null && !containsPluginInfo) {
      StatisticsUtil.addPluginInfoTo(pluginInfo, validatedData);
    }
    return validatedData;
  }

  private boolean isTestModeEnabled(@Nullable EventGroupRules rule) {
    return StatisticsRecorderUtil.isTestModeEnabled(myRecorderId) && rule != null &&
           ContainerUtil.exists(rule.getEventIdRules(), r -> r instanceof TestModeValidationRule);
  }

  public void update() {
    getValidationRulesStorage().update();
  }

  public void reload() {
    getValidationRulesStorage().reload();
  }

  private static class BlindSensitiveDataValidator extends IntellijSensitiveDataValidator {
    protected BlindSensitiveDataValidator(@NotNull IntellijValidationRulesStorage storage, @NotNull String recorderId) {
      super(storage, recorderId);
    }

    @Override
    public @NotNull String guaranteeCorrectEventId(@NotNull EventContext context, @Nullable EventGroupRules groupRules) {
      return context.eventId;
    }

    @Override
    public @NotNull Map<String, Object> guaranteeCorrectEventData(@NotNull EventContext context, EventGroupRules groupRules) {
      return context.eventData;
    }

    @Override
    public boolean isGroupAllowed(@NotNull EventLogGroup group) {
      return true;
    }
  }
}
