/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "StructuralSearchPlugin", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ConfigurationManager implements PersistentStateComponent<Element> {
  @NonNls static final String SEARCH_TAG_NAME = "searchConfiguration";
  @NonNls static final String REPLACE_TAG_NAME = "replaceConfiguration";
  @NonNls private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private final List<Configuration> configurations = new SmartList<>();
  private final List<Configuration> historyConfigurations = new SmartList<>();

  public static ConfigurationManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ConfigurationManager.class);
  }

  @Override
  public Element getState() {
    final Element state = new Element("state");
    writeConfigurations(state, configurations, historyConfigurations);
    return state;
  }

  @Override
  public void loadState(Element state) {
    configurations.clear();
    historyConfigurations.clear();
    readConfigurations(state, configurations, historyConfigurations);
  }

  public void addHistoryConfigurationToFront(Configuration configuration) {
    historyConfigurations.remove(configuration);
    historyConfigurations.add(0, configuration);
    configuration.setCreated(System.currentTimeMillis());
  }

  public void removeHistoryConfiguration(Configuration configuration) {
    historyConfigurations.remove(configuration);
  }

  public void addConfiguration(Configuration configuration) {
    configurations.remove(configuration);
    configurations.add(configuration);
  }

  public void removeConfiguration(Configuration configuration) {
    configurations.remove(configuration);
  }

  public static void writeConfigurations(@NotNull Element element,
                                         @NotNull Collection<Configuration> configurations,
                                         @NotNull Collection<Configuration> historyConfigurations) {
    for (final Configuration configuration : configurations) {
      saveConfiguration(element, configuration);
    }

    for (final Configuration historyConfiguration : historyConfigurations) {
      final Element infoElement = saveConfiguration(element, historyConfiguration);
      infoElement.setAttribute(SAVE_HISTORY_ATTR_NAME, "1");
    }
  }

  private static Element saveConfiguration(@NotNull Element element, @NotNull Configuration config) {
    final Element infoElement = new Element(config instanceof SearchConfiguration ? SEARCH_TAG_NAME : REPLACE_TAG_NAME);
    element.addContent(infoElement);
    config.writeExternal(infoElement);
    return infoElement;
  }

  public static void readConfigurations(@NotNull Element element,
                                        @NotNull Collection<Configuration> configurations,
                                        @NotNull Collection<Configuration> historyConfigurations) {
    for (final Element pattern : element.getChildren()) {
      final Configuration config = readConfiguration(pattern);
      if (config == null) continue;

      if (pattern.getAttribute(SAVE_HISTORY_ATTR_NAME) != null) {
        historyConfigurations.add(config);
      }
      else {
        configurations.add(config);
      }
    }
  }

  private static Configuration readConfiguration(@NotNull Element childElement) {
    final String name = childElement.getName();
    final Configuration config;
    if (name.equals(SEARCH_TAG_NAME)) {
      config = new SearchConfiguration();
    }
    else if (name.equals(REPLACE_TAG_NAME)) {
      config = new ReplaceConfiguration();
    }
    else {
      return null;
    }
    config.readExternal(childElement);
    return config;
  }

  /**
   * @return the names of all configurations, both user defined and built in.
   */
  public List<String> getAllConfigurationNames() {
    final Stream<Configuration> stream = Stream.concat(StructuralSearchUtil.getPredefinedTemplates().stream(), configurations.stream());
    return stream.map(c -> c.getName()).collect(Collectors.toList());
  }

  @NotNull
  public Collection<Configuration> getConfigurations() {
    return Collections.unmodifiableList(configurations);
  }

  @Nullable
  public Configuration findConfigurationByName(String name) {
    final Configuration configuration = findConfigurationByName(configurations, name);
    if (configuration != null) {
      return configuration;
    }
    return findConfigurationByName(StructuralSearchUtil.getPredefinedTemplates(), name);
  }

  @Nullable
  private static Configuration findConfigurationByName(final Collection<Configuration> configurations, final String name) {
    for(Configuration config : configurations) {
      if (config.getName().equals(name)) return config;
    }
    return null;
  }

  @NotNull
  public Collection<Configuration> getHistoryConfigurations() {
    return Collections.unmodifiableList(historyConfigurations);
  }

  @Nullable
  public static String findAppropriateName(@NotNull final Collection<Configuration> configurations, @NotNull String _name,
                                           @NotNull final Project project) {
    Configuration config;
    String name = _name;

    while ((config = findConfigurationByName(configurations, name)) != null) {
      final int i = Messages.showYesNoDialog(
        project,
        SSRBundle.message("overwrite.message"),
        SSRBundle.message("overwrite.title"),
        AllIcons.General.QuestionDialog
      );

      if (i == Messages.YES) {
        configurations.remove(config);
        break;
      }
      name = showSaveTemplateAsDialog(name, project);
      if (name == null) break;
    }
    return name;
  }

  @Nullable
  public static String showSaveTemplateAsDialog(@NotNull String initial, @NotNull Project project) {
    return Messages.showInputDialog(
      project,
      SSRBundle.message("template.name.button"),
      SSRBundle.message("save.template.description.button"),
      AllIcons.General.QuestionDialog,
      initial,
      new NonEmptyInputValidator()
    );
  }
}
