/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.openapi.components.*;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@State(
  name = GotItStateKeeper.COMPONENT_NAME,
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/gotIt.xml", 
                      roamingType = RoamingType.DISABLED)
)
public class GotItStateKeeper implements PersistentStateComponent<Element> {
  public static final String COMPONENT_NAME = "GotItState";
  
  private static final String ELEMENT_NAME = "disabledNotification";
  private static final String ATTRIBUTE_NAME = "key";

  private final Set<String> myDisabledNotifications = new THashSet<String>();

  public static GotItStateKeeper getInstance() {
    return ServiceManager.getService(GotItStateKeeper.class);
  }

  public synchronized boolean isNotificationDisabled(@NotNull String key) {
    return myDisabledNotifications.contains(key);
  }

  public synchronized void disableNotification(@NotNull String key) {
    myDisabledNotifications.add(key);
  }
  
  @Nullable
  @Override
  public synchronized Element getState() {
    Element element = new Element(COMPONENT_NAME);
    for (String key : myDisabledNotifications) {
      Element child = new Element(ELEMENT_NAME);
      child.setAttribute(ATTRIBUTE_NAME, key);
      element.addContent(child);
    }
    return element;
  }

  @Override
  public synchronized void loadState(Element state) {
    myDisabledNotifications.clear();
    for (Element child : state.getChildren(ELEMENT_NAME)) {
      String key = child.getAttributeValue(ATTRIBUTE_NAME);
      if (key != null) {
        myDisabledNotifications.add(key);
      }
    }
  }
}
