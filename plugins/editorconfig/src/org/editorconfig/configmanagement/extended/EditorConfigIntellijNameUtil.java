// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.LanguageCodeStylePropertyMapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EditorConfigIntellijNameUtil {
  public final static String IDE_PREFIX = "ij_";
  final static String GENERIC_PROPERTY_PREFIX = "any_";
  final static String GENERIC_OPTION_KEY_PREFIX = IDE_PREFIX + GENERIC_PROPERTY_PREFIX;

  private EditorConfigIntellijNameUtil() {
  }

  @NotNull
  public static List<String> toEditorConfigNames(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String propertyName) {
    if (isIgnored(propertyName)) {
      return Collections.emptyList();
    }
    switch (IntellijPropertyKindMap.getPropertyKind(propertyName)) {
      case EDITOR_CONFIG_STANDARD:
        return Collections.singletonList(propertyName);
      case UNSUPPORTED:
        break;
      case LANGUAGE:
        if (mapper instanceof LanguageCodeStylePropertyMapper) {
          return Collections.singletonList(getLanguageProperty(mapper, propertyName));
        }
        break;
      case COMMON:
        List<String> names = new ArrayList<>();
        names.add(GENERIC_OPTION_KEY_PREFIX + propertyName);
        names.add(getLanguageProperty(mapper, propertyName));
        return names;
      case GENERIC:
        return Collections.singletonList(IDE_PREFIX + propertyName);
      case JB_STANDARD:
        // Not supported;
        break;
    }
    return Collections.emptyList();
  }

  @NotNull
  public static String getLanguageProperty(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String propertyName) {
    return IDE_PREFIX + mapper.getLanguageDomainId() + "_" + propertyName;
  }

  @NotNull
  public static String toIntellijName(@NotNull String editorConfigName) {
    return StringUtil.trimStart(StringUtil.trimStart(editorConfigName, IDE_PREFIX), GENERIC_PROPERTY_PREFIX);
  }

  @Nullable
  public static String extractLanguageDomainId(@NotNull String propertyName) {
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
