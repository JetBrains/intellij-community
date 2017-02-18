/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import javax.swing.*;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@State(name = "TestHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class TestHistoryConfiguration implements PersistentStateComponent<TestHistoryConfiguration.State> {

  public static class State {

    private Map<String, ConfigurationBean> myHistoryElements = new LinkedHashMap<>();

    @Property(surroundWithTag = false)
    @MapAnnotation(surroundKeyWithTag = false, surroundWithTag = false, surroundValueWithTag = false, entryTagName = "history-entry", keyAttributeName = "file")
    public Map<String, ConfigurationBean> getHistoryElements() {
      return myHistoryElements;
    }

    public void setHistoryElements(final Map<String, ConfigurationBean> elements) {
      myHistoryElements = elements;
    }
  }

  @Tag("configuration")
  public static class ConfigurationBean {
    
    @Attribute("name")
    public String name;
    @Attribute("configurationId")
    public String configurationId;
  }

  private State myState = new State();

  public static TestHistoryConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, TestHistoryConfiguration.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }
  
  public Collection<String> getFiles() {
    return myState.getHistoryElements().keySet();
  }
  
  public String getConfigurationName(String file) {
    final ConfigurationBean bean = myState.getHistoryElements().get(file);
    return bean != null ? bean.name : null;
  }
  
  public Icon getIcon(String file) {
    final ConfigurationBean bean = myState.getHistoryElements().get(file);
    if (bean != null) {
      ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(bean.configurationId);
      if (type != null) return type.getIcon();
    }
    return null;
  }

  public void registerHistoryItem(String file, String configName, String configId) {
    final ConfigurationBean bean = new ConfigurationBean();
    bean.name = configName;
    bean.configurationId = configId;
    final Map<String, ConfigurationBean> historyElements = myState.getHistoryElements();
    historyElements.put(file, bean);
    if (historyElements.size() > AbstractImportTestsAction.getHistorySize()) {
      final String first = historyElements.keySet().iterator().next();
      historyElements.remove(first);
    }
  }
}
