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
package com.intellij.openapi.editor.colors.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class DefaultColorSchemesManager implements ApplicationComponent, JDOMExternalizable {
  private final ArrayList mySchemes;
  @NonNls private static final String SCHEME_ELEMENT = "scheme";

  public String getComponentName() {
    return "DefaultColorSchemesManager";
  }

  public DefaultColorSchemesManager() {
    mySchemes = new ArrayList();
  }

  public static DefaultColorSchemesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DefaultColorSchemesManager.class);
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    List schemes = element.getChildren(SCHEME_ELEMENT);
    for (Iterator iterator = schemes.iterator(); iterator.hasNext();) {
      Element schemeElement = (Element) iterator.next();
      DefaultColorsScheme newScheme = new DefaultColorsScheme(this);
      newScheme.readExternal(schemeElement);
      mySchemes.add(newScheme);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  public DefaultColorsScheme[] getAllSchemes() {
    return (DefaultColorsScheme[]) mySchemes.toArray(new DefaultColorsScheme[mySchemes.size()]);
  }

  public EditorColorsScheme getScheme(String name) {
    for (int i = 0; i < mySchemes.size(); i++) {
      DefaultColorsScheme scheme = (DefaultColorsScheme) mySchemes.get(i);
      if (name.equals(scheme.getName())) return scheme;
    }

    return null;
  }
}
