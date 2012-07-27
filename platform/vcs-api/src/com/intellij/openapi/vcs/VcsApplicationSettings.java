/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/27/12
 * Time: 8:51 PM
 */
@State(
  name = "VcsApplicationSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )
  }
)
public class VcsApplicationSettings implements PersistentStateComponent<Element> {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.VcsApplicationSettings");
  public String PATCH_STORAGE_LOCATION = null;

  public static VcsApplicationSettings getInstance() {
    return ServiceManager.getService(VcsApplicationSettings.class);
  }

  @Override
  public Element getState() {
    try {
      final Element e = new Element("state");
      if (PATCH_STORAGE_LOCATION != null) {
        e.setAttribute("PATCH_STORAGE_LOCATION", PATCH_STORAGE_LOCATION);
      }
      DefaultJDOMExternalizer.writeExternal(this, e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      Attribute location = state.getAttribute("PATCH_STORAGE_LOCATION");
      if (location != null) {
        PATCH_STORAGE_LOCATION = location.getValue();
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }
}
