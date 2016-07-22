/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class DefaultKeymap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.keymap.impl.DefaultKeymap");

  @NonNls
  private static final String KEY_MAP = "keymap";
  @NonNls
  private static final String NAME_ATTRIBUTE = "name";

  private final List<Keymap> myKeymaps = new ArrayList<>();

  public static DefaultKeymap getInstance() {
    return ServiceManager.getService(DefaultKeymap.class);
  }

  public DefaultKeymap() {
    for (BundledKeymapProvider provider : getProviders()) {
      final List<String> fileNames = provider.getKeymapFileNames();
      for (String fileName : fileNames) {
        try {
          loadKeymapsFromElement(JDOMUtil.loadResourceDocument(new URL("file:///idea/" + fileName)).getRootElement());
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  @NotNull
  protected BundledKeymapProvider[] getProviders() {
    return Extensions.getExtensions(BundledKeymapProvider.EP_NAME);
  }

  private void loadKeymapsFromElement(@NotNull Element element) throws InvalidDataException {
    for (Element child : element.getChildren(KEY_MAP)) {
      String keymapName = child.getAttributeValue(NAME_ATTRIBUTE);
      DefaultKeymapImpl keymap = keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP) ? new MacOSDefaultKeymap() : new DefaultKeymapImpl();
      keymap.readExternal(child, myKeymaps.toArray(new Keymap[myKeymaps.size()]));
      keymap.setName(keymapName);
      myKeymaps.add(keymap);
    }
  }

  @NotNull
  public Keymap[] getKeymaps() {
    return myKeymaps.toArray(new Keymap[myKeymaps.size()]);
  }

  public String getDefaultKeymapName() {
    if (SystemInfo.isMac) {
      return KeymapManager.MAC_OS_X_KEYMAP;
    }
    else if (SystemInfo.isXWindow) {
      if (SystemInfo.isKDE) {
        return KeymapManager.KDE_KEYMAP;
      }
      else {
        return KeymapManager.X_WINDOW_KEYMAP;
      }
    }
    else {
      return KeymapManager.DEFAULT_IDEA_KEYMAP;
    }
  }

  public String getKeymapPresentableName(@NotNull KeymapImpl keymap) {
    String name = keymap.getName();

    // Netbeans keymap is no longer for version 6.5, but we need to keep the id
    if ("NetBeans 6.5".equals(name)) {
      return "NetBeans";
    }

    return KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name) ? "Default" : name;
  }
}
