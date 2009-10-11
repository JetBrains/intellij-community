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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.VcsShowConfirmationOptionImpl;
import com.intellij.openapi.vcs.VcsShowOptionsSettingImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Map;

public class ProjectLevelVcsManagerSerialization {
  @NonNls private static final String OPTIONS_SETTING = "OptionsSetting";
  @NonNls private static final String CONFIRMATIONS_SETTING = "ConfirmationsSetting";
  @NonNls private static final String VALUE_ATTTIBUTE = "value";
  @NonNls private static final String ID_ATTRIBUTE = "id";

  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_DIRECTORY = "directory";
  @NonNls private static final String ATTRIBUTE_VCS = "vcs";
  @NonNls private static final String ATTRIBUTE_DEFAULT_PROJECT = "defaultProject";
  @NonNls private static final String ELEMENT_ROOT_SETTINGS = "rootSettings";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";

  // read-only can be kept here
  private final Map<String, VcsShowConfirmationOption.Value> myReadValue;

  public ProjectLevelVcsManagerSerialization() {
    myReadValue = new com.intellij.util.containers.HashMap<String, VcsShowConfirmationOption.Value>();
  }

  private VcsShowOptionsSettingImpl getOrCreateOption(final Map<String, VcsShowOptionsSettingImpl> options, final String actionName) {
    if (! options.containsKey(actionName)) {
      options.put(actionName, new VcsShowOptionsSettingImpl(actionName));
    }
    return options.get(actionName);
  }

  public void readExternalUtil(final Element element, final OptionsAndConfirmations optionsAndConfirmations) throws InvalidDataException {
    final Map<String, VcsShowOptionsSettingImpl> options = optionsAndConfirmations.getOptions();
    List subElements = element.getChildren(OPTIONS_SETTING);
    for (Object o : subElements) {
      if (o instanceof Element) {
        final Element subElement = ((Element)o);
        final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
        final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
        if (id != null && value != null) {
          try {
            final boolean booleanValue = Boolean.valueOf(value).booleanValue();
            getOrCreateOption(options, id).setValue(booleanValue);
          }
          catch (Exception e) {
            //ignore
          }
        }
      }
    }
    myReadValue.clear();
    subElements = element.getChildren(CONFIRMATIONS_SETTING);
    for (Object o : subElements) {
      if (o instanceof Element) {
        final Element subElement = ((Element)o);
        final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
        final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
        if (id != null && value != null) {
          try {
            myReadValue.put(id, VcsShowConfirmationOption.Value.fromString(value));
          }
          catch (Exception e) {
            //ignore
          }
        }
      }
    }
  }

  public void writeExternalUtil(final Element element, final OptionsAndConfirmations optionsAndConfirmations) throws WriteExternalException {
    final Map<String, VcsShowOptionsSettingImpl> options = optionsAndConfirmations.getOptions();
    final Map<String, VcsShowConfirmationOptionImpl> confirmations = optionsAndConfirmations.getConfirmations();
    
    for (VcsShowOptionsSettingImpl setting : options.values()) {
      final Element settingElement = new Element(OPTIONS_SETTING);
      element.addContent(settingElement);
      settingElement.setAttribute(VALUE_ATTTIBUTE, Boolean.toString(setting.getValue()));
      settingElement.setAttribute(ID_ATTRIBUTE, setting.getDisplayName());
    }

    for (VcsShowConfirmationOptionImpl setting : confirmations.values()) {
      final Element settingElement = new Element(CONFIRMATIONS_SETTING);
      element.addContent(settingElement);
      settingElement.setAttribute(VALUE_ATTTIBUTE, setting.getValue().toString());
      settingElement.setAttribute(ID_ATTRIBUTE, setting.getDisplayName());
    }
  }

  public VcsShowConfirmationOption.Value getInitOptionValue(final String id) {
    return myReadValue.get(id);
  }
}
