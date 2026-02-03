// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AbbreviationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
@State(name = "AbbreviationManager", storages = @Storage("abbrevs.xml"), category = SettingsCategory.KEYMAP)
public final class AbbreviationManagerImpl extends AbbreviationManager implements PersistentStateComponent<Element> {
  private final Map<String, List<String>> abbreviationToActionId = new HashMap<>();
  private final Map<String, Set<String>> actionIdToAbbreviations = new HashMap<>();
  private final Map<String, Set<String>> pluginsActionIdToAbbreviations = new HashMap<>();

  @Override
  public @NotNull Element getState() {
    final Element actions = new Element("actions");
    if (actionIdToAbbreviations.isEmpty()) {
      return actions;
    }

    Element result = null;
    for (Map.Entry<String, Set<String>> entry : actionIdToAbbreviations.entrySet()) {
      String key = entry.getKey();
      Set<String> abbreviations = entry.getValue();
      Set<String> pluginAbbreviations = pluginsActionIdToAbbreviations.get(key);
      if (Objects.equals(abbreviations, pluginAbbreviations)) {
        continue;
      }

      if (abbreviations != null) {
        if (result == null) {
          result = new Element("abbreviations");
          actions.addContent(result);
        }

        final Element action = new Element("action");
        action.setAttribute("id", key);
        result.addContent(action);
        for (String abbr : abbreviations) {
          Element abbreviation = new Element("abbreviation");
          abbreviation.setAttribute("name", abbr);
          action.addContent(abbreviation);
        }
      }
    }

    return actions;
  }

  @Override
  public void loadState(@NotNull Element state) {
    List<Element> abbreviations = state.getChildren("abbreviations");
    if (abbreviations.size() != 1) {
      return;
    }

    List<Element> actions = abbreviations.get(0).getChildren("action");
    for (Element action : actions) {
      String actionId = action.getAttributeValue("id");
      Set<String> values = actionIdToAbbreviations.computeIfAbsent(actionId, k -> new LinkedHashSet<>(1));
      for (Element abbr : action.getChildren("abbreviation")) {
        final String abbrValue = abbr.getAttributeValue("name");
        if (abbrValue != null) {
          values.add(abbrValue);
          abbreviationToActionId.computeIfAbsent(abbrValue, k -> new ArrayList<>()).add(actionId);
        }
      }
    }
  }

  @Override
  public @NotNull Set<String> getAbbreviations() {
    return actionIdToAbbreviations.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
  }

  @Override
  public @NotNull Set<String> getAbbreviations(@NotNull String actionId) {
    Set<String> abbreviations = actionIdToAbbreviations.get(actionId);
    return abbreviations == null ? Collections.emptySet() : Collections.unmodifiableSet(abbreviations);
  }

  @Override
  public @NotNull List<String> findActions(@NotNull String abbreviation) {
    final List<String> actions = abbreviationToActionId.get(abbreviation);
    return actions == null ? Collections.emptyList() : Collections.unmodifiableList(actions);
  }


  private static void register(@NotNull String abbreviation, @NotNull String actionId, @NotNull Map<String, Set<String>> storage) {
    storage.computeIfAbsent(actionId, k -> new LinkedHashSet<>(1)).add(abbreviation);
  }

  public void register(@NotNull String abbreviation, @NotNull String actionId, boolean fromPluginXml) {
    if (fromPluginXml && actionIdToAbbreviations.containsKey(actionId)) {
      register(abbreviation, actionId, pluginsActionIdToAbbreviations);
      return;
    }
    register(abbreviation, actionId, actionIdToAbbreviations);
    if (fromPluginXml) {
      register(abbreviation, actionId, pluginsActionIdToAbbreviations);
    }

    List<String> ids = abbreviationToActionId.computeIfAbsent(abbreviation, k -> new ArrayList<>(0));
    if (!ids.contains(actionId)) {
      ids.add(actionId);
    }
  }

  @Override
  public void register(@NotNull String abbreviation, @NotNull String actionId) {
    register(abbreviation, actionId, false);
  }

  @Override
  public void remove(@NotNull String abbreviation, @NotNull String actionId) {
    List<String> actions = abbreviationToActionId.get(abbreviation);
    if (actions != null) {
      actions.remove(actionId);
    }

    Set<String> abbreviations = actionIdToAbbreviations.get(actionId);
    if (abbreviations != null) {
      abbreviations.remove(abbreviation);
    }
    else {
      abbreviations = actionIdToAbbreviations.get(actionId);
      if (abbreviations != null) {
        Set<String> customValues = new LinkedHashSet<>(abbreviations);
        customValues.remove(abbreviation);
        actionIdToAbbreviations.put(actionId, customValues);
      }
    }
  }

  @Override
  public void removeAllAbbreviations(@NotNull String actionId) {
    Set<String> abbreviations = getAbbreviations(actionId);
    for (String abbreviation : abbreviations) {
      abbreviationToActionId.get(abbreviation).remove(actionId);
    }
    actionIdToAbbreviations.remove(actionId);
  }
}
