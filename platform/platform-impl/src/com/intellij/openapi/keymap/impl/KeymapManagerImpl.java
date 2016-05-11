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
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(
  name = "KeymapManager",
  storages = @Storage(value = "keymap.xml", roamingType = RoamingType.PER_OS),
  additionalExportFile = KeymapManagerImpl.KEYMAPS_DIR_PATH
)
public class KeymapManagerImpl extends KeymapManagerEx implements PersistentStateComponent<Element>, ApplicationComponent {
  static final String KEYMAPS_DIR_PATH = "keymaps";

  private final List<KeymapManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Map<String, String> myBoundShortcuts = new HashMap<String, String>();

  @NonNls private static final String ACTIVE_KEYMAP = "active_keymap";
  @NonNls private static final String NAME_ATTRIBUTE = "name";
  private final SchemesManager<Keymap, KeymapImpl> mySchemesManager;

  public static boolean ourKeymapManagerInitialized = false;

  KeymapManagerImpl(@NotNull DefaultKeymap defaultKeymap, @NotNull SchemesManagerFactory factory) {
    SchemeProcessor<KeymapImpl> schemeProcessor = new SchemeProcessor<KeymapImpl>() {
      @NotNull
      @Override
      public KeymapImpl readScheme(@NotNull Element element) throws InvalidDataException {
        KeymapImpl keymap = new KeymapImpl();
        keymap.readExternal(element, getAllIncludingDefaultsKeymaps());
        return keymap;
      }

      @Override
      public Element writeScheme(@NotNull final KeymapImpl scheme) {
        return scheme.writeExternal();
      }

      @NotNull
      @Override
      public State getState(@NotNull KeymapImpl scheme) {
        return scheme.canModify() ? State.POSSIBLY_CHANGED : State.NON_PERSISTENT;
      }

      @Override
      public void onCurrentSchemeChanged(@Nullable Scheme oldScheme) {
        Keymap keymap = mySchemesManager.getCurrentScheme();
        for (KeymapManagerListener listener : myListeners) {
          listener.activeKeymapChanged(keymap);
        }
      }
    };
    mySchemesManager = factory.create(KEYMAPS_DIR_PATH, schemeProcessor);

    String systemDefaultKeymap = WelcomeWizardUtil.getWizardMacKeymap() != null
                                 ? WelcomeWizardUtil.getWizardMacKeymap()
                                 : defaultKeymap.getDefaultKeymapName();
    for (Keymap keymap : defaultKeymap.getKeymaps()) {
      mySchemesManager.addScheme(keymap);
      if (keymap.getName().equals(systemDefaultKeymap)) {
        setActiveKeymap(keymap);
      }
    }
    mySchemesManager.loadSchemes();

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourKeymapManagerInitialized = true;
  }

  @Override
  public Keymap[] getAllKeymaps() {
    List<Keymap> keymaps = getKeymaps(Conditions.<Keymap>alwaysTrue());
    return keymaps.toArray(new Keymap[keymaps.size()]);
  }

  @NotNull
  public List<Keymap> getKeymaps(@NotNull Condition<Keymap> additionalFilter) {
    List<Keymap> result = new ArrayList<Keymap>();
    for (Keymap keymap : mySchemesManager.getAllSchemes()) {
      if (!keymap.getPresentableName().startsWith("$") && additionalFilter.value(keymap)) {
        result.add(keymap);
      }
    }
    return result;
  }

  public Keymap[] getAllIncludingDefaultsKeymaps() {
    Collection<Keymap> keymaps = mySchemesManager.getAllSchemes();
    return keymaps.toArray(new Keymap[keymaps.size()]);
  }

  @Override
  @Nullable
  public Keymap getKeymap(@NotNull String name) {
    return mySchemesManager.findSchemeByName(name);
  }

  @Override
  public Keymap getActiveKeymap() {
    return mySchemesManager.getCurrentScheme();
  }

  @Override
  public void setActiveKeymap(@Nullable Keymap keymap) {
    mySchemesManager.setCurrent(keymap);
  }

  @Override
  public void bindShortcuts(String sourceActionId, String targetActionId) {
    myBoundShortcuts.put(targetActionId, sourceActionId);
  }

  @Override
  public void unbindShortcuts(String targetActionId) {
    myBoundShortcuts.remove(targetActionId);
  }

  @Override
  public Set<String> getBoundActions() {
    return myBoundShortcuts.keySet();
  }

  @Override
  public String getActionBinding(String actionId) {
    Set<String> visited = null;
    String id = actionId, next;
    while ((next = myBoundShortcuts.get(id)) != null) {
      if (visited == null) visited = ContainerUtil.newHashSet();
      if (!visited.add(id = next)) break;
    }
    return Comparing.equal(id, actionId) ? null : id;
  }

  @Override
  public SchemesManager<Keymap, KeymapImpl> getSchemesManager() {
    return mySchemesManager;
  }

  public void setKeymaps(@NotNull List<Keymap> keymaps, @Nullable Keymap active, @Nullable Condition<Keymap> removeCondition) {
    mySchemesManager.setSchemes(keymaps, active, removeCondition);
  }

  @Override
  public Element getState() {
    Element result = new Element("component");
    if (mySchemesManager.getCurrentScheme() != null) {
      Element e = new Element(ACTIVE_KEYMAP);
      Keymap currentScheme = mySchemesManager.getCurrentScheme();
      if (currentScheme != null) {
        e.setAttribute(NAME_ATTRIBUTE, currentScheme.getName());
      }
      result.addContent(e);
    }
    return result;
  }

  @Override
  public void loadState(@NotNull Element state) {
    Element child = state.getChild(ACTIVE_KEYMAP);
    String activeKeymapName = child == null ? null : child.getAttributeValue(NAME_ATTRIBUTE);
    if (!StringUtil.isEmptyOrSpaces(activeKeymapName)) {
      mySchemesManager.setCurrentSchemeName(activeKeymapName);
    }
  }

  @Override
  public void addKeymapManagerListener(@NotNull KeymapManagerListener listener) {
    pollQueue();
    myListeners.add(listener);
  }

  @Override
  public void addKeymapManagerListener(@NotNull final KeymapManagerListener listener, @NotNull Disposable parentDisposable) {
    pollQueue();
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeKeymapManagerListener(listener);
      }
    });
  }

  private void pollQueue() {
    // assume it is safe to remove elements during iteration, as is the case with the COWAL
    for (KeymapManagerListener listener : myListeners) {
      if (listener instanceof WeakKeymapManagerListener && ((WeakKeymapManagerListener)listener).isDead()) {
        myListeners.remove(listener);
      }
    }
  }

  @Override
  public void removeKeymapManagerListener(@NotNull KeymapManagerListener listener) {
    pollQueue();
    myListeners.remove(listener);
  }

  @Override
  public void addWeakListener(@NotNull KeymapManagerListener listener) {
    addKeymapManagerListener(new WeakKeymapManagerListener(this, listener));
  }

  @Override
  public void removeWeakListener(@NotNull KeymapManagerListener listenerToRemove) {
    // assume it is safe to remove elements during iteration, as is the case with the COWAL
    for (KeymapManagerListener listener : myListeners) {
      if (listener instanceof WeakKeymapManagerListener && ((WeakKeymapManagerListener)listener).isWrapped(listenerToRemove)) {
        myListeners.remove(listener);
      }
    }
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "KeymapManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }
}
