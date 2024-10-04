// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProjectLevelVcsManagerSerialization {
  @NonNls private static final String OPTIONS_SETTING = "OptionsSetting";
  @NonNls private static final String CONFIRMATIONS_SETTING = "ConfirmationsSetting";
  @NonNls private static final String VALUE_ATTTIBUTE = "value";
  @NonNls private static final String ID_ATTRIBUTE = "id";

  public static void readExternalUtil(@NotNull Element element, @NotNull OptionsAndConfirmations optionsAndConfirmations)
    throws InvalidDataException {
    for (Element subElement : element.getChildren(OPTIONS_SETTING)) {
      final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
      final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
      if (id != null && value != null) {
        optionsAndConfirmations.setOptionValue(id, Boolean.parseBoolean(value));
      }
    }

    for (Element subElement : element.getChildren(CONFIRMATIONS_SETTING)) {
      final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
      final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
      if (id != null && value != null) {
        optionsAndConfirmations.setConfirmationValue(id, VcsShowConfirmationOption.Value.fromString(value));
      }
    }
  }

  public static void writeExternalUtil(@NotNull Element element, @NotNull OptionsAndConfirmations optionsAndConfirmations)
    throws WriteExternalException {
    optionsAndConfirmations.getOptionsValues().forEach((id, value) -> {
      if (!value) {
        Element settingElement = new Element(OPTIONS_SETTING);
        element.addContent(settingElement);
        settingElement.setAttribute(VALUE_ATTTIBUTE, Boolean.toString(value));
        settingElement.setAttribute(ID_ATTRIBUTE, id);
      }
    });

    optionsAndConfirmations.getConfirmationsValues().forEach((id, value) -> {
      if (value != VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
        final Element settingElement = new Element(CONFIRMATIONS_SETTING);
        element.addContent(settingElement);
        settingElement.setAttribute(VALUE_ATTTIBUTE, value.toString());
        settingElement.setAttribute(ID_ATTRIBUTE, id);
      }
    });
  }
}
