// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@State(name = "RecentsManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class RecentsManager implements PersistentStateComponent<Element> {
  @NonNls private static final String KEY_ELEMENT_NAME = "key";
  @NonNls private static final String RECENT_ELEMENT_NAME = "recent";
  @NonNls protected static final String NAME_ATTR = "name";

  private final Map<String, LinkedList<String>> myMap = new HashMap<>();
  private int myRecentsNumberToKeep = 5;

  @NotNull
  public static RecentsManager getInstance(@NotNull Project project) {
    return project.getService(RecentsManager.class);
  }

  @Nullable
  public List<String> getRecentEntries(@NotNull String key) {
    return myMap.get(key);
  }

  public void registerRecentEntry(@NotNull String key, String recentEntry) {
    LinkedList<String> recents = myMap.get(key);
    if (recents == null) {
      recents = new LinkedList<>();
      myMap.put(key, recents);
    }

    add(recents, recentEntry);
  }

  private void add(final LinkedList<? super String> recentEntries, final String newEntry) {
    final int oldIndex = recentEntries.indexOf(newEntry);
    if (oldIndex >= 0) {
      recentEntries.remove(oldIndex);
    }
    else if (recentEntries.size() == myRecentsNumberToKeep) {
      recentEntries.removeLast();
    }

    recentEntries.addFirst(newEntry);
  }

  @Override
  public void loadState(@NotNull Element element) {
    myMap.clear();
    for (Element keyElement : element.getChildren(KEY_ELEMENT_NAME)) {
      LinkedList<String> recents = new LinkedList<>();
      for (Element aChildren : keyElement.getChildren(RECENT_ELEMENT_NAME)) {
        recents.addLast(aChildren.getAttributeValue(NAME_ATTR));
      }

      myMap.put(keyElement.getAttributeValue(NAME_ATTR), recents);
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    for (Map.Entry<String, LinkedList<String>> entry : myMap.entrySet()) {
      Element keyElement = new Element(KEY_ELEMENT_NAME);
      keyElement.setAttribute(NAME_ATTR, entry.getKey());
      for (String recent : entry.getValue()) {
        keyElement.addContent(new Element(RECENT_ELEMENT_NAME).setAttribute(NAME_ATTR, recent));
      }
      element.addContent(keyElement);
    }
    return element;
  }

  public void setRecentsNumberToKeep(final int recentsNumberToKeep) {
    myRecentsNumberToKeep = recentsNumberToKeep;
  }
}
