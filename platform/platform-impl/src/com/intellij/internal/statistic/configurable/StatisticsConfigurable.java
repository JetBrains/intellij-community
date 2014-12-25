/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.configurable;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StatisticsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

    private boolean modifiedByDefault;

    public StatisticsConfigurable() {
        this(false);
    }

    public StatisticsConfigurable(boolean isModifiedByDefault) {
        modifiedByDefault = isModifiedByDefault;
    }


    private StatisticsConfigurationComponent myConfig;

  @Nls
  public String getDisplayName() {
    return "Usage Statistics";
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.usage.statictics";
  }

  public JComponent createComponent() {
    myConfig = new StatisticsConfigurationComponent();
    return myConfig.getJComponent();
  }

  public boolean isModified() {
    final UsageStatisticsPersistenceComponent persistenceComponent = UsageStatisticsPersistenceComponent.getInstance();
    return myConfig.isAllowed() != persistenceComponent.isAllowed() ||
           myConfig.getPeriod() != persistenceComponent.getPeriod() ||
           modifiedByDefault;
  }

  public void apply() throws ConfigurationException {
    final UsageStatisticsPersistenceComponent persistenceComponent = UsageStatisticsPersistenceComponent.getInstance();

    persistenceComponent.setPeriod(myConfig.getPeriod());
    persistenceComponent.setAllowed(myConfig.isAllowed());
    persistenceComponent.setShowNotification(false);
    modifiedByDefault = false;
  }

  public void reset() {
    myConfig.reset();
  }

  public void disposeUIResources() {
    myConfig = null;
  }

  @NotNull
  @Override
  public String getId() {
    return "usage.statistics";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }
}
