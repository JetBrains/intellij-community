// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "IntentionsCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED),
    @Storage(value = "statistics.intentions.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public class IntentionsCollector extends BaseUICollector implements PersistentStateComponent<IntentionsCollector.State> {
  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  private static final List<String> PREFIXES_TO_STRIP = Arrays.asList("com.intellij.codeInsight.",
                                                                      "com.intellij.");
  public void record(@NotNull IntentionAction action, @NotNull Language language) {
    State state = getState();
    if (state == null) return;

    String id = getIntentionId(action);

    String key = language.getID() + " " + id;
    final Integer count = state.myIntentions.get(key);
    int value = count == null ? 1 : count + 1;
    state.myIntentions.put(key, value);
  }

  @NotNull
  private String getIntentionId(@NotNull IntentionAction action) {
    Object handler = action;
    if (action instanceof IntentionActionDelegate) {
      IntentionAction delegate = ((IntentionActionDelegate)action).getDelegate();
      if (delegate != action) {
        return getIntentionId(delegate);
      }
    } else if (action instanceof QuickFixWrapper) {
      LocalQuickFix fix = ((QuickFixWrapper)action).getFix();
      if (fix != action) {
        handler = fix;
      }
    }

    String fqn = handler.getClass().getName();
    for (String prefix : PREFIXES_TO_STRIP) {
      fqn = StringUtil.trimStart(fqn, prefix);
    }

    if (isNotBundledPluginClass(handler.getClass())) {
      fqn = "[!]" + fqn;
    }

    return fqn;
  }

  public static IntentionsCollector getInstance() {
    return ServiceManager.getService(IntentionsCollector.class);
  }

  public final static class State {
    @Tag("Intentions")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "name", valueAttributeName = "count")
    public Map<String, Integer> myIntentions = new HashMap<>();
  }
}

