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
package com.intellij.ui;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@State(name = "RecentsManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class RecentsManager implements PersistentStateComponent<Element> {
  @NonNls private static final String KEY_ELEMENT_NAME = "key";
  @NonNls private static final String RECENT_ELEMENT_NAME = "recent";
  @NonNls protected static final String NAME_ATTR = "name";

  private final Map<String, LinkedList<String>> myMap = new THashMap<>();
  private int myRecentsNumberToKeep = 5;

  @NotNull
  public static RecentsManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, RecentsManager.class);
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

  private void add(final LinkedList<String> recentEntries, final String newEntry) {
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
  public void loadState(Element element) {
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
