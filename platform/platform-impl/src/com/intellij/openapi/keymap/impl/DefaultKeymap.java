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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.net.URL;

/**
 * @author Eugene Belyaev
 */
public class DefaultKeymap implements JDOMExternalizable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.keymap.impl.DefaultKeymap");

  @NonNls
  private static final String KEY_MAP = "keymap";
  @NonNls
  private static final String NAME_ATTRIBUTE = "name";

  private ArrayList<Keymap> myKeymaps = new ArrayList<Keymap>();

  public static DefaultKeymap getInstance() {
    return ApplicationManager.getApplication().getComponent(DefaultKeymap.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void readExternal(Element element) throws InvalidDataException{
    myKeymaps = new ArrayList<Keymap>();
    loadKeymapsFromElement(element);

    for(BundledKeymapProvider provider: Extensions.getExtensions(BundledKeymapProvider.EP_NAME)) {
      final List<String> fileNames = provider.getKeymapFileNames();
      for (String fileName : fileNames) {
        try {
          final Document document = JDOMUtil.loadResourceDocument(new URL("file:///idea/" + fileName));
          loadKeymapsFromElement(document.getRootElement());
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  private void loadKeymapsFromElement(final Element element) throws InvalidDataException {
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element child=(Element)i.next();
      if (KEY_MAP.equals(child.getName())) {
        String keymapName = child.getAttributeValue(NAME_ATTRIBUTE);
        DefaultKeymapImpl keymap = KeymapManager.MAC_OS_X_KEYMAP.equals(keymapName)
                                   ? new MacOSDefaultKeymap()
                                   : new DefaultKeymapImpl();
        keymap.readExternal(child, myKeymaps.toArray(new Keymap[myKeymaps.size()]));
        keymap.setName(keymapName);
        myKeymaps.add(keymap);
      }
    }
  }

  /**
   * We override this method to disable saving the keymap.
   */
  public void writeExternal(Element element) throws WriteExternalException{
    throw new WriteExternalException();
  }

  public Keymap[] getKeymaps() {
    return myKeymaps.toArray(new Keymap[myKeymaps.size()]);
  }

  public String getComponentName() {
    return "DefaultKeymap";
  }

}