// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.google.common.collect.Lists;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.themes.metadata.UIThemeMetadataService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class ThemeJsonUtil {

  @NonNls
  private static final String UI_PROPERTY_NAME = "ui";

  @NonNls
  private static final String COLORS_PROPERTY_NAME = "colors";

  static boolean isInsideUiProperty(@NotNull JsonProperty property) {
    PsiElement parent = property;
    while ((parent = parent.getParent()) != null) {
      if (!(parent instanceof JsonProperty)) continue;
      if (UI_PROPERTY_NAME.equals(((JsonProperty)parent).getName())) return true;
    }
    return false;
  }

  static String getParentNames(@NotNull JsonProperty property) {
    List<JsonProperty> parentProperties = PsiTreeUtil.collectParents(property, JsonProperty.class, false, e -> {
      //TODO check that it is TOP-LEVEL 'ui'  property
      return e instanceof JsonProperty && UI_PROPERTY_NAME.equals(((JsonProperty)e).getName());
    });

    return Lists.reverse(parentProperties).stream()
      .map(p -> p.getName())
      .collect(Collectors.joining("."));
  }

  static boolean isThemeFilename(@NotNull String fileName) {
    return StringUtil.endsWithIgnoreCase(fileName, ".theme.json");
  }

  static List<JsonProperty> getNamedColors(@NotNull JsonFile themeFile) {
    JsonValue topLevelValue = themeFile.getTopLevelValue();
    if (!(topLevelValue instanceof JsonObject)) return Collections.emptyList();
    JsonProperty colorsProperty = ((JsonObject)topLevelValue).findProperty(COLORS_PROPERTY_NAME);
    if (colorsProperty == null) return Collections.emptyList();
    final JsonValue colorsValue = colorsProperty.getValue();
    if (!(colorsValue instanceof JsonObject)) return Collections.emptyList();
    return ((JsonObject)colorsValue).getPropertyList();
  }

  @Nullable
  static Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> findMetadata(@NotNull JsonProperty property) {
    final String key = property.getName();
    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> byName = UIThemeMetadataService.getInstance().findByKey(key);
    if (byName != null) return byName;

    return UIThemeMetadataService.getInstance().findByKey(getParentNames(property) + "." + key);
  }
}
