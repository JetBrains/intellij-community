// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

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
  private static final int MAX_RECENT_SIZE = 30;
  @NonNls static final String SEARCH_TAG_NAME = "searchConfiguration";
  @NonNls static final String REPLACE_TAG_NAME = "replaceConfiguration";
  @NonNls private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private final List<Configuration> configurations = new SmartList<>();
  private final List<Configuration> historyConfigurations = new SmartList<>();
  private final Project myProject;

  public static ConfigurationManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ConfigurationManager.class);
  }

  public ConfigurationManager(Project project) {
    myProject = project;
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

  public void addHistoryConfiguration(Configuration configuration) {
    configuration = configuration.copy();
    historyConfigurations.remove(configuration); // move to most recent
    configuration.setCreated(System.currentTimeMillis());
    historyConfigurations.add(0, configuration);
    while (historyConfigurations.size() > MAX_RECENT_SIZE) {
      historyConfigurations.remove(historyConfigurations.size() - 1);
    }
  }

  public Configuration getMostRecentConfiguration() {
    return historyConfigurations.isEmpty() ? null : historyConfigurations.get(0);
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
    return configuration != null ? configuration : findConfigurationByName(StructuralSearchUtil.getPredefinedTemplates(), name);
  }

  @Nullable
  private static Configuration findConfigurationByName(final Collection<Configuration> configurations, final String name) {
    return configurations.stream().filter(config -> config.getName().equals(name)).findFirst().orElse(null);
  }

  @NotNull
  public List<Configuration> getHistoryConfigurations() {
    return Collections.unmodifiableList(historyConfigurations);
  }

  public boolean showSaveTemplateAsDialog(@NotNull Configuration newConfiguration) {
    return showSaveTemplateAsDialog(configurations, newConfiguration, myProject);
  }

  public static boolean showSaveTemplateAsDialog(@NotNull Collection<Configuration> configurations,
                                                 @NotNull Configuration newConfiguration,
                                                 @NotNull Project project) {
    String name = showInputDialog(newConfiguration.getName(), project);
    Configuration config;
    while ((config = findConfigurationByName(configurations, name)) != null && name !=  null) {
     final int answer =
        Messages.showYesNoDialog(
          project,
          SSRBundle.message("overwrite.message"),
          SSRBundle.message("overwrite.title", name),
          "Replace",
          Messages.CANCEL_BUTTON,
          Messages.getQuestionIcon()
        );
      if (answer == Messages.YES) {
        configurations.remove(config);
        break;
      }
      name = showInputDialog(name, project);
    }
    if (name != null) {
      newConfiguration.setName(name);
      configurations.add(newConfiguration.copy());
      return true;
    }
    return false;
  }

  @Nullable
  private static String showInputDialog(@NotNull String initial, @NotNull Project project) {
    return Messages.showInputDialog(
      project,
      SSRBundle.message("template.name.button"),
      SSRBundle.message("save.template.description.button"),
      Messages.getQuestionIcon(),
      initial,
      new NonEmptyInputValidator()
    );
  }
}
