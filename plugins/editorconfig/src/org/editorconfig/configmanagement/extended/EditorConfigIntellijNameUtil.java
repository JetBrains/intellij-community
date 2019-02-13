// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorConfigIntellijNameUtil {
  final static String IDE_PREFIX = "ij_";
  final static String GENERIC_PROPERTY_PREFIX = "any_";
  final static String GENERIC_OPTION_KEY_PREFIX = IDE_PREFIX + GENERIC_PROPERTY_PREFIX;

  private EditorConfigIntellijNameUtil() {
  }

  public static List<String> toEditorConfigNames(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String propertyName) {
    List<String> names = ContainerUtil.newArrayList();
    CodeStylePropertyAccessor accessor = mapper.getAccessor(propertyName);
    if (accessor.isGenericProperty()) {
      names.add(IDE_PREFIX + GENERIC_PROPERTY_PREFIX + propertyName);
    }
    String langPrefix = mapper.getLanguageDomainId() + "_";
    names.add(IDE_PREFIX + langPrefix + propertyName);
    return names;
  }

  @Nullable
  public static String toIntellijName(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String editorConfigName) {
    if (mapper.containsProperty(editorConfigName)) {
      return editorConfigName;
    }
    else if (editorConfigName.startsWith(IDE_PREFIX)) {
      String withoutSuffix = editorConfigName.substring(IDE_PREFIX.length());
      if (mapper.containsProperty(withoutSuffix)) {
        return withoutSuffix;
      }
      int nextUnderscorePos = withoutSuffix.indexOf('_');
      if (nextUnderscorePos > 0 && nextUnderscorePos < withoutSuffix.length()) {
        withoutSuffix = withoutSuffix.substring(nextUnderscorePos + 1);
        if (editorConfigName.contains(withoutSuffix)) {
          return withoutSuffix;
        }
      }
    }
    return null;
  }

  static String getIdeLangPrefix(@NotNull AbstractCodeStylePropertyMapper mapper) {
    return IDE_PREFIX + mapper.getLanguageDomainId() + "_";
  }
}
