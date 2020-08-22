// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.VcsShowConfirmationOptionImpl;
import com.intellij.openapi.vcs.VcsShowOptionsSettingImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class ProjectLevelVcsManagerSerialization {
  @NonNls private static final String OPTIONS_SETTING = "OptionsSetting";
  @NonNls private static final String CONFIRMATIONS_SETTING = "ConfirmationsSetting";
  @NonNls private static final String VALUE_ATTTIBUTE = "value";
  @NonNls private static final String ID_ATTRIBUTE = "id";

  public void readExternalUtil(@NotNull Element element, @NotNull OptionsAndConfirmations optionsAndConfirmations) throws InvalidDataException {
    for (Element subElement : element.getChildren(OPTIONS_SETTING)) {
      final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
      final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
      if (id != null && value != null) {
        VcsShowOptionsSettingImpl option = optionsAndConfirmations.getOrCreateOption(id);
        option.setValue(Boolean.parseBoolean(value));
      }
    }

    for (Element subElement : element.getChildren(CONFIRMATIONS_SETTING)) {
      final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
      final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
      if (id != null && value != null) {
        VcsShowConfirmationOptionImpl confirmation = optionsAndConfirmations.getConfirmation(id);
        if (confirmation != null) {
          confirmation.setValue(VcsShowConfirmationOption.Value.fromString(value));
        }
      }
    }
  }

  public void writeExternalUtil(@NotNull Element element, @NotNull OptionsAndConfirmations optionsAndConfirmations) throws WriteExternalException {
    final Map<String, VcsShowOptionsSettingImpl> options = optionsAndConfirmations.getOptions();
    final Map<String, VcsShowConfirmationOptionImpl> confirmations = optionsAndConfirmations.getConfirmations();

    for (VcsShowOptionsSettingImpl setting : options.values()) {
      if (!setting.getValue()) {
        Element settingElement = new Element(OPTIONS_SETTING);
        element.addContent(settingElement);
        settingElement.setAttribute(VALUE_ATTTIBUTE, Boolean.toString(setting.getValue()));
        settingElement.setAttribute(ID_ATTRIBUTE, setting.getDisplayName());
      }
    }

    for (VcsShowConfirmationOptionImpl setting : confirmations.values()) {
      if (setting.getValue() != VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
        final Element settingElement = new Element(CONFIRMATIONS_SETTING);
        element.addContent(settingElement);
        settingElement.setAttribute(VALUE_ATTTIBUTE, setting.getValue().toString());
        settingElement.setAttribute(ID_ATTRIBUTE, setting.getDisplayName());
      }
    }
  }
}
