// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ThemeJsonUtil {
  private static final @NonNls String UI_PROPERTY_NAME = "ui";
  private static final @NonNls String COLORS_PROPERTY_NAME = "colors";

  private static final Pattern GROUP_MASK_PATTERN = Pattern.compile("(\\.Group[\\d+])");
  private static final Pattern COLOR_MASK_PATTERN = Pattern.compile("(\\.Color[\\d+])");
  private static final Pattern FRACTION_MASK_PATTERN = Pattern.compile("(\\.Fraction[\\d+])");

  private static final String GROUP_N = ".GroupN";
  private static final String COLOR_N = ".ColorN";
  private static final String FRACTION_N = ".FractionN";

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

  public static boolean isThemeFilename(@NotNull String fileName) {
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

  static @Nullable Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> findMetadata(@NotNull JsonProperty property) {
    String key = property.getName();

    Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> byName = UIThemeMetadataService.getInstance().findByKey(key);
    if (byName != null) return byName;

    String fullKey = getParentNames(property) + "." + key;
    if (looksLikeNumberedMetaKey(fullKey)) {
      fullKey = GROUP_MASK_PATTERN.matcher(fullKey).replaceAll(GROUP_N);
      fullKey = FRACTION_MASK_PATTERN.matcher(fullKey).replaceAll(FRACTION_N);
      fullKey = COLOR_MASK_PATTERN.matcher(fullKey).replaceAll(COLOR_N);
    }

    return UIThemeMetadataService.getInstance().findByKey(fullKey);
  }

  private static boolean looksLikeNumberedMetaKey(String key) {
    return key.contains(".Group")
           || key.contains(".Color")
           || key.contains(".Fraction");
  }
}
