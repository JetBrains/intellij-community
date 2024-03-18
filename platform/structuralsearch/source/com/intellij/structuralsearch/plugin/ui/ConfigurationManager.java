// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
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

@State(name = "StructuralSearchPlugin", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ConfigurationManager implements PersistentStateComponent<Element> {
  private static final int MAX_RECENT_SIZE = 30;
  private static final int RECENT_CONFIGURATION_NAME_LENGTH = 40;
  @NonNls private static final String SEARCH_TAG_NAME = "searchConfiguration";
  @NonNls private static final String REPLACE_TAG_NAME = "replaceConfiguration";
  @NonNls private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private final List<Configuration> configurations = new SmartList<>();
  private final List<Configuration> historyConfigurations = new SmartList<>();
  private final ConfigurationManagerState myIdeState;
  private final ProjectConfigurationManagerState myProjectState;
  private final Project myProject;
  private boolean myLastSaveWasInProject = false;

  public static ConfigurationManager getInstance(@NotNull Project project) {
    return project.getService(ConfigurationManager.class);
  }

  public ConfigurationManager(Project project) {
    myProject = project;
    myIdeState = ConfigurationManagerState.getInstance();
    myProjectState = ProjectConfigurationManagerState.getInstance(project);
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
      addHistoryConfiguration(configuration);
    }
    Collections.reverse(historyConfigurations);
  }
  /**
   * Stores configurations at the application level. Before the configurations where stored in the project workspace file.
   */
  private void migrate(@NotNull List<? extends Configuration> configurations) {
    if (configurations.isEmpty()) {
      return;
    }
    outer:
    for (Configuration configuration : configurations) {
      Configuration existing = myIdeState.get(configuration.getRefName());
      while (existing != null) {
        if (configuration.equals(existing)) {
          continue outer;
        }
        configuration.setName(configuration.getName() + '~');
        existing = myIdeState.get(configuration.getRefName());
      }
      myIdeState.add(configuration);
    }
    configurations.clear();
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
    myIdeState.add(configuration);
  }

  public void removeConfiguration(Configuration configuration, boolean ide) {
    (ide ? myIdeState : myProjectState).remove(configuration.getRefName());
  }

  public static void writeConfigurations(@NotNull Element element, @NotNull Collection<? extends Configuration> configurations) {
    writeConfigurations(element, configurations, Collections.emptyList());
  }

  private static void writeConfigurations(@NotNull Element element,
                                          @NotNull Collection<? extends Configuration> configurations,
                                          @NotNull Collection<? extends Configuration> historyConfigurations) {
    for (Configuration configuration : configurations) {
      configuration.getMatchOptions().setScope(null);
      saveConfiguration(element, configuration);
    }

    for (Configuration historyConfiguration : historyConfigurations) {
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
    for (Element pattern : element.getChildren()) {
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
   * @return the names of all configurations, both user defined, from the project and built in.
   */
  public List<String> getAllConfigurationNames() {
    return ContainerUtil.map(getAllConfigurations(), c -> c.getRefName());
  }

  public List<Configuration> getAllConfigurations() {
    return ContainerUtil.concat(StructuralSearchUtil.getPredefinedTemplates(), getIdeConfigurations(), getProjectConfigurations());
  }

  @NotNull
  public List<Configuration> getIdeConfigurations() {
    return myIdeState.getAll();
  }

  @NotNull
  public List<Configuration> getProjectConfigurations() {
    return myProjectState.getAll();
  }

  @Nullable
  public Configuration findConfigurationByName(String name) {
    Configuration projectConfiguration = myProjectState.get(name); // project overrides local
    if (projectConfiguration != null) return projectConfiguration;
    final Configuration ideConfiguration = myIdeState.get(name);
    if (ideConfiguration != null) return ideConfiguration;
    return ContainerUtil.find(StructuralSearchUtil.getPredefinedTemplates(), config -> config.getRefName().equals(name));
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
    for (Configuration configuration : historyConfigurations) {
      configuration.getMatchOptions().initScope(myProject);
    }
    return Collections.unmodifiableList(historyConfigurations);
  }

  public boolean showSaveTemplateAsDialog(@NotNull Configuration newConfiguration) {
    Pair<@NlsSafe String, Boolean> nameAndProject = showInputDialog(newConfiguration.getName());
    while (nameAndProject.first != null && (nameAndProject.second ? myProjectState : myIdeState).get(nameAndProject.first) != null) {
      final int answer =
        Messages.showYesNoDialog(
          myProject,
          SSRBundle.message("overwrite.message"),
          SSRBundle.message("overwrite.title", nameAndProject.first),
          SSRBundle.message("button.replace"),
          Messages.getCancelButton(),
          Messages.getQuestionIcon()
        );
      if (answer == Messages.YES) {
        break;
      }
      nameAndProject = showInputDialog(nameAndProject.first);
    }
    if (nameAndProject.first != null) {
      newConfiguration.setName(nameAndProject.first);
      newConfiguration.setUuid(null);
      myLastSaveWasInProject = nameAndProject.second;
      (nameAndProject.second ? myProjectState : myIdeState).add(newConfiguration.copy());
      return true;
    }
    return false;
  }

  /**
   * @return the name entered by the user, or null if the dialog was cancelled
   */
  private @NotNull Pair<@Nullable @NlsSafe String, Boolean> showInputDialog(@NotNull String initial) {
    return Messages.showInputDialogWithCheckBox(SSRBundle.message("template.name.label"),
                                                SSRBundle.message("save.template.title"),
                                                SSRBundle.message("checkbox.save.in.project"),
                                                myLastSaveWasInProject,
                                                true,
                                                Messages.getQuestionIcon(),
                                                initial,
                                                null);
  }

  @Service(Service.Level.PROJECT)
  @State(name = "StructuralSearch", storages = @Storage("structuralSearch.xml"), category = SettingsCategory.CODE)
  private static final class ProjectConfigurationManagerState extends AbstractConfigurationManagerState {
    public static ProjectConfigurationManagerState getInstance(Project project) {
      return project.getService(ProjectConfigurationManagerState.class);
    }
  }

  @State(name = "StructuralSearch", storages = @Storage("structuralSearch.xml"), category = SettingsCategory.CODE)
  private static final class ConfigurationManagerState extends AbstractConfigurationManagerState {
    public static ConfigurationManagerState getInstance() {
      return ApplicationManager.getApplication().getService(ConfigurationManagerState.class);
    }
  }

  private static sealed abstract class AbstractConfigurationManagerState implements PersistentStateComponent<Element> {

    public final Map<String, Configuration> configurations = new LinkedHashMap<>();

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

    public List<Configuration> getAll() {
      return new ArrayList<>(configurations.values());
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
