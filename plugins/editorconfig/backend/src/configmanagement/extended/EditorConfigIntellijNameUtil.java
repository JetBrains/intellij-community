// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.LanguageCodeStylePropertyMapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EditorConfigIntellijNameUtil {
  public static final String IDE_PREFIX = "ij_";
  static final String GENERIC_PROPERTY_PREFIX = "any_";
  static final String GENERIC_OPTION_KEY_PREFIX = IDE_PREFIX + GENERIC_PROPERTY_PREFIX;

  private EditorConfigIntellijNameUtil() {
  }

  public static @NotNull List<String> toEditorConfigNames(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String propertyName) {
    if (isIgnored(propertyName)) {
      return Collections.emptyList();
    }
    return switch (IntellijPropertyKindMap.getPropertyKind(propertyName)) {
      case EDITOR_CONFIG_STANDARD -> List.of(propertyName);
      case LANGUAGE -> mapper instanceof LanguageCodeStylePropertyMapper
            ? List.of(getLanguageProperty(mapper, propertyName))
            : List.of();
      case COMMON -> List.of(GENERIC_OPTION_KEY_PREFIX + propertyName, getLanguageProperty(mapper, propertyName));
      case GENERIC -> List.of(IDE_PREFIX + propertyName);
      case UNSUPPORTED, JB_STANDARD -> List.of(); // Not supported;
    };
  }

  public static @NotNull String getLanguageProperty(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String propertyName) {
    return IDE_PREFIX + mapper.getLanguageDomainId() + "_" + propertyName;
  }

  public static @NotNull String toIntellijName(@NotNull String editorConfigName) {
    return StringUtil.trimStart(StringUtil.trimStart(editorConfigName, IDE_PREFIX), GENERIC_PROPERTY_PREFIX);
  }

  public static @Nullable String extractLanguageDomainId(@NotNull String propertyName) {
    if (propertyName.startsWith(IDE_PREFIX)) {
      String id = StringUtil.trimStart(propertyName, IDE_PREFIX);
      int separatorPos = id.indexOf("_");
      if (separatorPos > 0) {
        id = id.substring(0, separatorPos);
        return id;
      }
    }
    return null;
  }

  public static boolean isIndentProperty(@NotNull String propertyName) {
    propertyName = StringUtil.trimStart(propertyName, IDE_PREFIX);
    return "indent_size".equals(propertyName) ||
           "indent_style".equals(propertyName) ||
           "tab_width".equals(propertyName) ||
           "continuation_indent_size".equals(propertyName);
  }

  public static boolean isIgnored(@NotNull String propertyName) {
    // TODO<rv>: Provide an API instead of a hardcoded value
    return "uniform_indent".equals(propertyName);
  }

}
