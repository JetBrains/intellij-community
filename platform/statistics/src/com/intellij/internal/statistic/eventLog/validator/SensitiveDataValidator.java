// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.*;
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesStorage;
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesStorageProvider;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.internal.statistic.eventLog.EventLogSystemEvents.SYSTEM_EVENTS;
import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;
import static com.intellij.internal.statistic.utils.StatisticsUtilKt.addPluginInfoTo;

/**
 * <p>
 *   The data from all collectors is validated before it's recorded locally.
 *   It's necessary to make sure that the data is correct and it doesn't contain personal or proprietary information.<br/>
 *   Validation is performed right before logging in {@link SensitiveDataValidator#guaranteeCorrectEventId(EventLogGroup, EventContext)}
 *   and {@link SensitiveDataValidator#guaranteeCorrectEventData(EventLogGroup, EventContext)}.<br/>
 * </p>
 *
 * <p>
 *   Therefore, each collector should define data scheme and rules which will be used in validation.<br/>
 *   Rules are stored in a separate repository, IDE loads rules from the server during runtime.<br/>
 *   To register rules for a new group or change existing ones, create an <a href="https://youtrack.jetbrains.com/issues/FUS">issue</a>.
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
public class SensitiveDataValidator {
  private static final ConcurrentMap<String, SensitiveDataValidator> ourInstances = new ConcurrentHashMap<>();
  protected final @NotNull ValidationRulesStorage myRulesStorage;

  static {
    CustomValidationRule.EP_NAME.addChangeListener(ourInstances::clear, null);
    CustomWhiteListRule.EP_NAME.addChangeListener(ourInstances::clear, null);
  }

  public static @NotNull SensitiveDataValidator getInstance(@NotNull String recorderId) {
    return ourInstances.computeIfAbsent(
      recorderId,
      id -> {
        ValidationRulesStorage storage = ValidationRulesStorageProvider.newStorage(recorderId);
        return ApplicationManager.getApplication().isUnitTestMode()
               ? new BlindSensitiveDataValidator(storage)
               : new SensitiveDataValidator(storage);
      }
    );
  }

  public static @Nullable SensitiveDataValidator getIfInitialized(@NotNull String recorderId) {
    return ourInstances.get(recorderId);
  }

  protected SensitiveDataValidator(@NotNull ValidationRulesStorage storage) {
    myRulesStorage = storage;
  }

  public ValidationRulesStorage getValidationRulesStorage() {
    return myRulesStorage;
  }

  public String guaranteeCorrectEventId(@NotNull EventLogGroup group,
                                        @NotNull EventContext context) {
    if (myRulesStorage.isUnreachable()) return UNREACHABLE_METADATA.getDescription();
    if (SYSTEM_EVENTS.contains(context.eventId)) return context.eventId;

    ValidationResultType validationResultType = validateEvent(group, context);
    return validationResultType == ACCEPTED ? context.eventId : validationResultType.getDescription();
  }

  public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
    EventGroupRules groupRules = myRulesStorage.getGroupRules(group.getId());
    if (isTestModeEnabled(groupRules)) {
      return context.eventData;
    }

    Map<String, Object> validatedData =
      new ConcurrentHashMap<>(); // TODO: don't create validatedData map if all keys are accepted (just return context.eventData)
    for (Map.Entry<String, Object> entry : context.eventData.entrySet()) {
      String key = entry.getKey();
      Object entryValue = entry.getValue();

      validatedData.put(key, validateEventData(context, groupRules, key, entryValue));
    }

    boolean containsPluginInfo = validatedData.containsKey("plugin") ||
                                 validatedData.containsKey("plugin_type") ||
                                 validatedData.containsKey("plugin_version");
    if (context.pluginInfo != null && !containsPluginInfo) {
      addPluginInfoTo(context.pluginInfo, validatedData);
    }
    return validatedData;
  }

  private static boolean isTestModeEnabled(@Nullable EventGroupRules rule) {
    return TestModeValidationRule.isTestModeEnabled() && rule != null &&
           Arrays.stream(rule.getEventIdRules()).anyMatch(r -> r instanceof TestModeValidationRule);
  }

  public ValidationResultType validateEvent(@NotNull EventLogGroup group, @NotNull EventContext context) {
    EventGroupRules groupRules = myRulesStorage.getGroupRules(group.getId());
    if (groupRules == null || !groupRules.areEventIdRulesDefined()) {
      return UNDEFINED_RULE; // there are no rules (eventId and eventData) to validate
    }

    return groupRules.validateEventId(context);
  }

  private Object validateEventData(@NotNull EventContext context,
                                   @Nullable EventGroupRules groupRules,
                                   @NotNull String key,
                                   @NotNull Object entryValue) {
    if (myRulesStorage.isUnreachable()) return UNREACHABLE_METADATA;
    if (groupRules == null) return UNDEFINED_RULE;
    return groupRules.validateEventData(key, entryValue, context);
  }

  public void update() {
    myRulesStorage.update();
  }

  public void reload() {
    myRulesStorage.reload();
  }

  private static class BlindSensitiveDataValidator extends SensitiveDataValidator {
    protected BlindSensitiveDataValidator(@NotNull ValidationRulesStorage storage) {
      super(storage);
    }

    @Override
    public String guaranteeCorrectEventId(@NotNull EventLogGroup group, @NotNull EventContext context) {
      return context.eventId;
    }

    @Override
    public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
      return context.eventData;
    }
  }
}
