/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

@State(
  name = "KeymapManager",
  roamingType = RoamingType.PER_PLATFORM,
  storages = {
    @Storage(
        id="keymap",
        file = "$APP_CONFIG$/keymap.xml"
    )}
)
public class KeymapManagerImpl extends KeymapManagerEx implements PersistentStateComponent<Element>, ExportableApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.keymap.KeymapManager");

  private final List<KeymapManagerListener> myListeners = ContainerUtil.createEmptyCOWList();
  private String myActiveKeymapName;
  private final Map<String, String> myBoundShortcuts = new HashMap<String, String>();

  @NonNls private static final String KEYMAP = "keymap";
  @NonNls private static final String KEYMAPS = "keymaps";
  @NonNls private static final String ACTIVE_KEYMAP = "active_keymap";
  @NonNls private static final String NAME_ATTRIBUTE = "name";
  private final SchemesManager<Keymap, KeymapImpl> mySchemesManager;

  public static boolean ourKeymapManagerInitialized = false;

  KeymapManagerImpl(DefaultKeymap defaultKeymap, SchemesManagerFactory factory) {
    mySchemesManager = factory.createSchemesManager(
        "$ROOT_CONFIG$/keymaps",
        new BaseSchemeProcessor<KeymapImpl>(){
          public KeymapImpl readScheme(final Document schemeContent) throws InvalidDataException, IOException, JDOMException {
            return readKeymap(schemeContent);
          }

          public Document writeScheme(final KeymapImpl scheme) throws WriteExternalException {
            return new Document(scheme.writeExternal());
          }

          public boolean shouldBeSaved(final KeymapImpl scheme) {
            return scheme.canModify();
          }
        },
        RoamingType.PER_USER);

    Keymap[] keymaps = defaultKeymap.getKeymaps();
    for (Keymap keymap : keymaps) {
      addKeymap(keymap);
      String systemDefaultKeymap = SystemInfo.isMac ? MAC_OS_X_KEYMAP : DEFAULT_IDEA_KEYMAP;
      if (systemDefaultKeymap.equals(keymap.getName())) {
        setActiveKeymap(keymap);
      }
    }
    load();
    ourKeymapManagerInitialized = true;
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{new File(PathManager.getOptionsPath()+File.separatorChar+"keymap.xml"),getKeymapDirectory(true)};
  }

  @NotNull
  public String getPresentableName() {
    return KeyMapBundle.message("key.maps.name");
  }

  public Keymap[] getAllKeymaps() {
    Collection<Keymap> keymaps = mySchemesManager.getAllSchemes();
    return keymaps.toArray(new Keymap[keymaps.size()]);
  }

  @Nullable
  public Keymap getKeymap(String name) {
    return mySchemesManager.findSchemeByName( name);
  }

  public Keymap getActiveKeymap() {
    return mySchemesManager.getCurrentScheme();
  }

  public void setActiveKeymap(Keymap activeKeymap) {
    mySchemesManager.setCurrentSchemeName(activeKeymap == null ? null : activeKeymap.getName());
    fireActiveKeymapChanged();
  }

  public void bindShortcuts(String sourceActionId, String targetActionId) {
    myBoundShortcuts.put(targetActionId, sourceActionId);
  }

  public Set<String> getBoundActions() {
    return myBoundShortcuts.keySet();
  }

  public String getActionBinding(String actionId) {
    return myBoundShortcuts.get(actionId);
  }

  public SchemesManager<Keymap, KeymapImpl> getSchemesManager() {
    return mySchemesManager;
  }

  public void addKeymap(Keymap keymap) {
    mySchemesManager.addNewScheme(keymap, true);
  }

  public void removeAllKeymapsExceptUnmodifiable() {
    for (Keymap keymap : mySchemesManager.getAllSchemes()) {
      if (keymap.canModify()) {
        mySchemesManager.removeScheme(keymap);
      }
    }
    mySchemesManager.setCurrentSchemeName(null);

    Collection<Keymap> keymaps = mySchemesManager.getAllSchemes();
    if (keymaps.size() > 0) {
      mySchemesManager.setCurrentSchemeName(keymaps.iterator().next().getName());
    }
  }

  public String getExternalFileName() {
    return "keymap";
  }

  public Element getState() {
    Element result = new Element("component");
    try {
      writeExternal(result);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return result;
  }

  public void loadState(final Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void readExternal(Element element) throws InvalidDataException{
    Element child = element.getChild(ACTIVE_KEYMAP);
    if (child != null) {
      myActiveKeymapName = child.getAttributeValue(NAME_ATTRIBUTE);
    }

    if (myActiveKeymapName != null) {
      Keymap keymap = getKeymap(myActiveKeymapName);
      if (keymap != null) {
        setActiveKeymap(keymap);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException{
    if (mySchemesManager.getCurrentScheme() != null) {
      Element e = new Element(ACTIVE_KEYMAP);
      Keymap currentScheme = mySchemesManager.getCurrentScheme();
      if (currentScheme != null) {
        e.setAttribute(NAME_ATTRIBUTE, currentScheme.getName());
      }
      element.addContent(e);
    }
  }

  private void load(){
    mySchemesManager.loadSchemes();
  }

  private KeymapImpl readKeymap(Document document) throws JDOMException,InvalidDataException, IOException{
    if (document == null) throw new InvalidDataException();
    Element root = document.getRootElement();
    if (root == null || !KEYMAP.equals(root.getName())) {
      throw new InvalidDataException();
    }
    KeymapImpl keymap = new KeymapImpl();
    keymap.readExternal(root, getAllKeymaps());

    return keymap;
  }

  @Nullable
  private static File getKeymapDirectory(boolean toCreate) {
    String directoryPath = PathManager.getConfigPath() + File.separator + KEYMAPS;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!toCreate) return null;
      if (!directory.mkdir()) {
        LOG.error("Cannot create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }

  private void fireActiveKeymapChanged() {
    for (KeymapManagerListener listener : myListeners) {
      listener.activeKeymapChanged(mySchemesManager.getCurrentScheme());
    }
  }

  public void addKeymapManagerListener(KeymapManagerListener listener) {
    myListeners.add(listener);
  }

  public void removeKeymapManagerListener(KeymapManagerListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public String getComponentName() {
    return "KeymapManager";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
