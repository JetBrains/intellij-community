// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@State(name = "TestHistory", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)
})
public class TestHistoryConfiguration implements PersistentStateComponent<TestHistoryConfiguration.State> {

  public static class State {

    private Map<String, ConfigurationBean> myHistoryElements = new LinkedHashMap<>();

    @Property(surroundWithTag = false)
    @XMap(entryTagName = "history-entry", keyAttributeName = "file")
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
  public void loadState(@NotNull State state) {
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
