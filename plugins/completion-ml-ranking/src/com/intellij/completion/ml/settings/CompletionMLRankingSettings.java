// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.settings;

import com.intellij.completion.ml.experiments.ExperimentInfo;
import com.intellij.completion.ml.experiments.ExperimentStatus;
import com.intellij.completion.ml.ranker.ExperimentModelProvider;
import com.intellij.completion.ml.sorting.RankingSupport;
import com.intellij.internal.ml.completion.RankingModelProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Don't use this class for disabling ML ranking from external plugins. Use {@link com.intellij.completion.ml.CompletionMLPolicy} instead.
 */
@State(name = "CompletionMLRankingSettings", storages = @Storage(value = "completionMLRanking.xml", roamingType = RoamingType.DISABLED))
public final class CompletionMLRankingSettings implements PersistentStateComponent<CompletionMLRankingSettings.State> {
  private static final Logger LOG = Logger.getInstance(CompletionMLRankingSettings.class);

  private final State myState;

  public CompletionMLRankingSettings() {
    myState = new State();
  }

  public static @NotNull CompletionMLRankingSettings getInstance() {
    return ApplicationManager.getApplication().getService(CompletionMLRankingSettings.class);
  }

  public boolean isRankingEnabled() {
    return myState.rankingEnabled;
  }

  public boolean isShowDiffEnabled() {
    return myState.showDiff;
  }

  public boolean isDecorateRelevantEnabled() {
    return myState.decorateRelevant;
  }

  public void setRankingEnabled(boolean value) {
    if (value == isRankingEnabled()) return;
    myState.rankingEnabled = value;
    disableExperiment();
    triggerSettingsChanged(value);
  }

  public boolean isLanguageEnabled(@NotNull String rankerId) {
    return myState.language2state.getOrDefault(rankerId, false);
  }

  public void setLanguageEnabled(@NotNull String rankerId, boolean isEnabled) {
    if (isEnabled == isLanguageEnabled(rankerId)) return;
    myState.language2state.put(rankerId, isEnabled);
    logCompletionState(rankerId, isEnabled);
    disableExperiment();
    if (isRankingEnabled()) {
      // log only if language ranking settings changes impact completion behavior
      MLCompletionSettingsCollector.rankingSettingsChanged(rankerId, isEnabled, isEnabledByDefault(rankerId), true);
    }
  }

  public void setShowDiffEnabled(boolean isEnabled) {
    if (isEnabled == isShowDiffEnabled()) return;
    myState.showDiff = isEnabled;
    disableExperiment();
    MLCompletionSettingsCollector.decorationSettingChanged(isEnabled);
  }

  public void setDecorateRelevantEnabled(boolean isEnabled) {
    myState.decorateRelevant = isEnabled;
  }

  @ApiStatus.Internal
  public void updateShowDiffInExperiment(boolean isEnabled) {
    myState.showDiff = isEnabled;
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState.rankingEnabled = state.rankingEnabled && state.language2state.containsValue(true);
    myState.showDiff = state.showDiff;
    myState.decorateRelevant = state.decorateRelevant;
    myState.language2state.putAll(state.language2state);
  }

  private void logCompletionState(@NotNull String languageName, boolean isEnabled) {
    final boolean enabled = myState.rankingEnabled && isEnabled;
    final boolean showDiff = enabled && myState.showDiff;
    LOG.info("ML Completion " + (enabled ? "enabled" : "disabled") + " ,show diff " + (showDiff ? "on" : "off") + " for: " + languageName);
  }

  private static void disableExperiment() {
    ExperimentStatus status = ExperimentStatus.Companion.getInstance();
    if (!status.isDisabled()) {
      LOG.info("Leave A/B ML completion experiment group");
    }
    status.disable();
  }

  private static boolean isEnabledByDefault(@NotNull String rankerId) {
    return ExperimentModelProvider.enabledByDefault().contains(rankerId);
  }

  private void triggerSettingsChanged(boolean enabled) {
    for (String ranker : getEnabledRankers()) {
      MLCompletionSettingsCollector.rankingSettingsChanged(ranker, enabled, isEnabledByDefault(ranker), false);
    }
  }

  private List<String> getEnabledRankers() {
    return myState.language2state.entrySet().stream()
      .filter(x -> x.getValue())
      .map(x -> x.getKey())
      .collect(Collectors.toList());
  }

  public static final class State {
    public boolean rankingEnabled = true;
    public boolean showDiff = false;
    public boolean decorateRelevant = false;

    public final Map<String, Boolean> language2state = new HashMap<>();

    public State() {
      ExperimentStatus experimentStatus = ExperimentStatus.Companion.getInstance();
      boolean isEAP = ApplicationInfo.getInstance().isEAP();
      for (Language language : Language.getRegisteredLanguages()) {
        RankingModelProvider ranker = RankingSupport.INSTANCE.findProviderSafe(language);
        if (ranker != null) {
          ExperimentInfo experimentInfo = experimentStatus.forLanguage(language);
          if (!experimentStatus.isDisabled() && experimentInfo.getInExperiment()) {
            boolean useMLRanking = experimentInfo.getShouldRank();
            language2state.put(ranker.getId(), useMLRanking);
            if (useMLRanking) {
              LOG.info("ML Completion enabled, experiment group=" + experimentInfo.getVersion()
                       + " for: " + language.getDisplayName());
            }
          }
          else {
            language2state.put(ranker.getId(), true);
          }
        }
      }
    }
  }
}
