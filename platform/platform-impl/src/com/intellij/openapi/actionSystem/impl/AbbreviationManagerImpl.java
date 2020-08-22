// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AbbreviationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "AbbreviationManager", storages = @Storage(value = "abbreviations.xml", roamingType = RoamingType.PER_OS))
public final class AbbreviationManagerImpl extends AbbreviationManager implements PersistentStateComponent<Element> {
  private final Map<String, List<String>> myAbbreviation2ActionId = new HashMap<>();
  private final Map<String, Set<String>> myActionId2Abbreviations = new HashMap<>();
  private final Map<String, Set<String>> myPluginsActionId2Abbreviations = new HashMap<>();

  @Override
  public @NotNull Element getState() {
    final Element actions = new Element("actions");
    if (myActionId2Abbreviations.isEmpty()) {
      return actions;
    }

    Element abbreviations = null;
    for (Map.Entry<String, Set<String>> entry : myActionId2Abbreviations.entrySet()) {
      String key = entry.getKey();
      Set<String> abbrs = entry.getValue();
      Set<String> pluginAbbrs = myPluginsActionId2Abbreviations.get(key);
      if (Objects.equals(abbrs, pluginAbbrs)) {
        continue;
      }

      if (abbrs != null) {
        if (abbreviations == null) {
          abbreviations = new Element("abbreviations");
          actions.addContent(abbreviations);
        }

        final Element action = new Element("action");
        action.setAttribute("id", key);
        abbreviations.addContent(action);
        for (String abbr : abbrs) {
          final Element abbreviation = new Element("abbreviation");
          abbreviation.setAttribute("name", abbr);
          action.addContent(abbreviation);
        }
      }
    }

    return actions;
  }

  @Override
  public void loadState(@NotNull Element state) {
    final List<Element> abbreviations = state.getChildren("abbreviations");
    if (abbreviations.size() == 1) {
      final List<Element> actions = abbreviations.get(0).getChildren("action");
      for (Element action : actions) {
        final String actionId = action.getAttributeValue("id");
        Set<String> values = myActionId2Abbreviations.computeIfAbsent(actionId, k -> new LinkedHashSet<>(1));
        for (Element abbr : action.getChildren("abbreviation")) {
          final String abbrValue = abbr.getAttributeValue("name");
          if (abbrValue != null) {
            values.add(abbrValue);
            myAbbreviation2ActionId.computeIfAbsent(abbrValue, k -> new ArrayList<>()).add(actionId);
          }
        }
      }
    }
  }

  @NotNull
  @Override
  public Set<String> getAbbreviations() {
    return myActionId2Abbreviations.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
  }

  @NotNull
  @Override
  public Set<String> getAbbreviations(@NotNull String actionId) {
    Set<String> abbreviations = myActionId2Abbreviations.get(actionId);
    if (abbreviations == null) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(abbreviations);
  }

  @NotNull
  @Override
  public List<String> findActions(@NotNull String abbreviation) {
    final List<String> actions = myAbbreviation2ActionId.get(abbreviation);
    return actions == null ? Collections.emptyList() : Collections.unmodifiableList(actions);
  }


  private static void register(@NotNull String abbreviation, @NotNull String actionId, @NotNull Map<String, Set<String>> storage) {
    storage.computeIfAbsent(actionId, k -> new LinkedHashSet<>(1)).add(abbreviation);
  }

  public void register(@NotNull String abbreviation, @NotNull String actionId, boolean fromPluginXml) {
    if (fromPluginXml && myActionId2Abbreviations.containsKey(actionId)) {
      register(abbreviation, actionId, myPluginsActionId2Abbreviations);
      return;
    }
    register(abbreviation, actionId, myActionId2Abbreviations);
    if (fromPluginXml) {
      register(abbreviation, actionId, myPluginsActionId2Abbreviations);
    }

    List<String> ids = myAbbreviation2ActionId.computeIfAbsent(abbreviation, k -> new ArrayList<>(0));

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
    final List<String> actions = myAbbreviation2ActionId.get(abbreviation);
    if (actions != null) {
      actions.remove(actionId);
    }
    Set<String> abbreviations = myActionId2Abbreviations.get(actionId);
    if (abbreviations != null) {
      abbreviations.remove(abbreviation);
    }
    else {
      Set<String> abbrs = myActionId2Abbreviations.get(actionId);
      if (abbrs != null) {
        Set<String> customValues = new LinkedHashSet<>(abbrs);
        customValues.remove(abbreviation);
        myActionId2Abbreviations.put(actionId, customValues);
      }
    }
  }

  @Override
  public void removeAllAbbreviations(@NotNull String actionId) {
    Set<String> abbreviations = getAbbreviations(actionId);
    for (String abbreviation : abbreviations) {
      myAbbreviation2ActionId.get(abbreviation).remove(actionId);
    }
    myActionId2Abbreviations.remove(actionId);
  }
}
