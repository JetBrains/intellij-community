// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "StructuralSearchPlugin", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ConfigurationManager implements PersistentStateComponent<Element> {
  private static final int MAX_RECENT_SIZE = 30;
  private static final int RECENT_CONFIGURATION_NAME_LENGTH = 40;
  @NonNls private static final String SEARCH_TAG_NAME = "searchConfiguration";
  @NonNls private static final String REPLACE_TAG_NAME = "replaceConfiguration";
  @NonNls private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private final List<Configuration> configurations = new SmartList<>();
  private final List<Configuration> historyConfigurations = new SmartList<>();
  private final ConfigurationManagerState myApplicationState;
  private final Project myProject;

  public static ConfigurationManager getInstance(@NotNull Project project) {
    return project.getService(ConfigurationManager.class);
  }

  public ConfigurationManager(Project project) {
    myProject = project;
    myApplicationState = ConfigurationManagerState.getInstance();
  }

  @Override
  public Element getState() {
    final Element state = new Element("state");
    writeConfigurations(state, configurations, historyConfigurations);
    return state;
  }

  @Override
  public void loadState(@NotNull Element element) {
    configurations.clear();
    historyConfigurations.clear();
    final SmartList<Configuration> tmp = new SmartList<>();
    readConfigurations(element, configurations, tmp);
    migrate(configurations);
    for (Configuration configuration : tmp) {
      configuration.getMatchOptions().initScope(myProject);
      addHistoryConfiguration(configuration);
    }
    Collections.reverse(historyConfigurations);
  }
  /**
   * Stores configurations at the application level. Before the configurations where stored in the workspace file.
   */
  private void migrate(@NotNull List<? extends Configuration> configurations) {
    if (configurations.isEmpty()) {
      return;
    }
    outer:
    for (Configuration configuration : configurations) {
      Configuration existing = myApplicationState.get(configuration.getRefName());
      while (existing != null) {
        if (configuration.equals(existing)) {
          continue outer;
        }
        configuration.setName(configuration.getName() + '~');
        existing = myApplicationState.get(configuration.getRefName());
      }
      myApplicationState.add(configuration);
    }
  }

  public void addHistoryConfiguration(@NotNull Configuration configuration) {
    configuration = configuration.copy();
    if (configuration.getCreated() <= 0) {
      configuration.setCreated(System.currentTimeMillis());
    }
    final var searchTemplate = configuration.getMatchOptions().getSearchPattern();
    configuration.setName(searchTemplate.length() < RECENT_CONFIGURATION_NAME_LENGTH ? searchTemplate : searchTemplate.substring(0, RECENT_CONFIGURATION_NAME_LENGTH).trim() + "…");
    final Configuration old = findConfiguration(historyConfigurations, configuration);
    if (old != null) historyConfigurations.remove(old); // move to most recent
    historyConfigurations.add(0, configuration);
    while (historyConfigurations.size() > MAX_RECENT_SIZE) {
      historyConfigurations.remove(historyConfigurations.size() - 1);
    }
  }

  public Configuration getMostRecentConfiguration() {
    return historyConfigurations.isEmpty() ? null : historyConfigurations.get(0);
  }

  @TestOnly
  public void addConfiguration(Configuration configuration) {
    myApplicationState.add(configuration);
  }

  public void removeConfiguration(Configuration configuration) {
    if (Registry.is("ssr.save.templates.to.ide.instead.of.project.workspace")) {
      myApplicationState.remove(configuration.getRefName());
    }
    configurations.remove(configuration);
  }

  public static void writeConfigurations(@NotNull Element element, @NotNull Collection<? extends Configuration> configurations) {
    writeConfigurations(element, configurations, Collections.emptyList());
  }

  private static void writeConfigurations(@NotNull Element element,
                                          @NotNull Collection<? extends Configuration> configurations,
                                          @NotNull Collection<? extends Configuration> historyConfigurations) {
    for (final Configuration configuration : configurations) {
      configuration.getMatchOptions().setScope(null);
      saveConfiguration(element, configuration);
    }

    for (final Configuration historyConfiguration : historyConfigurations) {
      final Element infoElement = saveConfiguration(element, historyConfiguration);
      infoElement.setAttribute(SAVE_HISTORY_ATTR_NAME, "1");
    }
  }

  @NotNull
  private static Element saveConfiguration(@NotNull Element element, @NotNull Configuration config) {
    final Element infoElement = new Element(config instanceof SearchConfiguration ? SEARCH_TAG_NAME : REPLACE_TAG_NAME);
    element.addContent(infoElement);
    config.writeExternal(infoElement);
    return infoElement;
  }

  public static void readConfigurations(@NotNull Element element, @NotNull Collection<? super Configuration> configurations) {
    readConfigurations(element, configurations, new SmartList<>());
  }

  private static void readConfigurations(@NotNull Element element,
                                         @NotNull Collection<? super Configuration> configurations,
                                         @NotNull Collection<? super Configuration> historyConfigurations) {
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

  private static Configuration readConfiguration(@NotNull Element element) {
    final String name = element.getName();
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
    config.readExternal(element);
    return config;
  }

  /**
   * @return the names of all configurations, both user defined and built in.
   */
  public List<String> getAllConfigurationNames() {
    final Stream<Configuration> stream = Stream.concat(StructuralSearchUtil.getPredefinedTemplates().stream(), getConfigurations().stream());
    return stream.map(c -> c.getRefName()).collect(Collectors.toList());
  }

  public List<Configuration> getAllConfigurations() {
    final Stream<Configuration> stream = Stream.concat(StructuralSearchUtil.getPredefinedTemplates().stream(), getConfigurations().stream());
    return stream.collect(Collectors.toList());
  }

  @NotNull
  public Collection<Configuration> getConfigurations() {
    if (Registry.is("ssr.save.templates.to.ide.instead.of.project.workspace")) {
      return myApplicationState.getAll();
    }
    else {
      return Collections.unmodifiableList(configurations);
    }
  }

  @Nullable
  public Configuration findConfigurationByName(String name) {
    if (Registry.is("ssr.save.templates.to.ide.instead.of.project.workspace")) {
      final Configuration ideConfiguration = myApplicationState.get(name);
      if (ideConfiguration != null) return ideConfiguration;
    }
    else {
      final Configuration configuration = findConfigurationByName(configurations, name);
      if (configuration != null) return configuration;
    }
    return findConfigurationByName(StructuralSearchUtil.getPredefinedTemplates(), name);
  }

  @Nullable
  private static Configuration findConfigurationByName(Collection<? extends Configuration> configurations, final String name) {
    return ContainerUtil.find(configurations, config -> config.getRefName().equals(name));
  }

  @Nullable
  private static Configuration findConfiguration(@NotNull Collection<? extends Configuration> configurations, Configuration configuration) {
    return ContainerUtil.find(configurations, c -> {
      if (configuration instanceof ReplaceConfiguration) {
        return c instanceof ReplaceConfiguration &&
               c.getMatchOptions().getSearchPattern().equals(configuration.getMatchOptions().getSearchPattern()) &&
               c.getReplaceOptions().getReplacement().equals(configuration.getReplaceOptions().getReplacement());
      }
      else {
        return c instanceof SearchConfiguration && c.getMatchOptions().getSearchPattern().equals(
          configuration.getMatchOptions().getSearchPattern());
      }
    });
  }

  @NotNull
  public List<Configuration> getHistoryConfigurations() {
    return Collections.unmodifiableList(historyConfigurations);
  }

  public boolean showSaveTemplateAsDialog(@NotNull Configuration newConfiguration) {
    if (Registry.is("ssr.save.templates.to.ide.instead.of.project.workspace")) {
      return showSaveTemplateAsDialog(newConfiguration, myProject,
                                      n -> myApplicationState.get(n) != null,
                                      c -> {
                                        myApplicationState.add(c);
                                        configurations.remove(c);
                                        configurations.add(c);
                                      });
    }
    return showSaveTemplateAsDialog(configurations, newConfiguration, myProject);
  }

  public static boolean showSaveTemplateAsDialog(@NotNull Configuration newConfiguration,
                                                 @NotNull Project project,
                                                 @NotNull Predicate<? super String> nameExistsPredicate,
                                                 @NotNull Consumer<? super Configuration> namedConfigurationConsumer) {
    String name = showInputDialog(newConfiguration.getName(), project);
    while (name != null && nameExistsPredicate.test(name)) {
      final int answer =
        Messages.showYesNoDialog(
          project,
          SSRBundle.message("overwrite.message"),
          SSRBundle.message("overwrite.title", name),
          SSRBundle.message("button.replace"),
          Messages.getCancelButton(),
          Messages.getQuestionIcon()
        );
      if (answer == Messages.YES) {
        break;
      }
      name = showInputDialog(name, project);
    }
    if (name != null) {
      newConfiguration.setName(name);
      newConfiguration.setUuid(null);
      namedConfigurationConsumer.accept(newConfiguration.copy());
      return true;
    }
    return false;
  }

  public static boolean showSaveTemplateAsDialog(@NotNull Collection<Configuration> configurations,
                                                 @NotNull Configuration newConfiguration,
                                                 @NotNull Project project) {
    return showSaveTemplateAsDialog(newConfiguration, project,
                                    n -> findConfigurationByName(configurations, n) != null,
                                    c -> {
                                      configurations.remove(c);
                                      configurations.add(c);
                                    });
  }

  /**
   * @return the name entered by the user, or null if the dialog was cancelled
   */
  private static @Nullable @NlsSafe String showInputDialog(@NotNull String initial, @NotNull Project project) {
    return Messages.showInputDialog(
      project,
      SSRBundle.message("template.name.button"),
      SSRBundle.message("save.template.description.button"),
      Messages.getQuestionIcon(),
      initial,
      null
    );
  }

  @State(name = "StructuralSearch", storages = @Storage("structuralSearch.xml"), category = SettingsCategory.CODE)
  private static class ConfigurationManagerState implements PersistentStateComponent<Element> {

    public final Map<String, Configuration> configurations = new LinkedHashMap<>();

    public static ConfigurationManagerState getInstance() {
      return ApplicationManager.getApplication().getService(ConfigurationManagerState.class);
    }

    public static ConfigurationManagerState getInstance(Project project) {
      return project.getService(ConfigurationManagerState.class);
    }

    public void add(Configuration configuration) {
      configuration.getMatchOptions().setScope(null);
      configurations.put(configuration.getRefName(), configuration);
    }

    public Configuration get(String name) {
      return configurations.get(name);
    }

    public void remove(String name) {
      configurations.remove(name);
    }

    public Collection<Configuration> getAll() {
      return Collections.unmodifiableCollection(configurations.values());
    }

    @Nullable
    @Override
    public Element getState() {
      final Element element = new Element("state");
      for (Configuration configuration : configurations.values()) {
        saveConfiguration(element, configuration);
      }
      return element;
    }

    @Override
    public void loadState(@NotNull Element state) {
      for (Element child : state.getChildren()) {
        final Configuration configuration = readConfiguration(child);
        if (configuration != null) {
          configurations.put(configuration.getRefName(), configuration);
        }
      }
    }
  }
}
