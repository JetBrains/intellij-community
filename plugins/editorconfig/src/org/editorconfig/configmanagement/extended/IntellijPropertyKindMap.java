// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.editorconfig.configmanagement.extended.EditorConfigPropertyKind.*;

public class IntellijPropertyKindMap {
  private final static Map<String, EditorConfigPropertyKind> PROPERTY_KIND_MAP = ContainerUtil.newHashMap();

  static {
    collectCommonLanguageProperties();
    PROPERTY_KIND_MAP.put("max_line_length", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("indent_size", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("indent_style", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("insert_final_new_line", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("end_of_line", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("trim_trailing_whitespace", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("tab_width", EDITOR_CONFIG_STANDARD);
    PROPERTY_KIND_MAP.put("charset", EDITOR_CONFIG_STANDARD);

    PROPERTY_KIND_MAP.put("formatter_off_tag", GENERIC);
    PROPERTY_KIND_MAP.put("formatter_on_tag", GENERIC);
    PROPERTY_KIND_MAP.put("formatter_tags_enabled", GENERIC);
    PROPERTY_KIND_MAP.put("visual_guides", GENERIC);
    PROPERTY_KIND_MAP.put("wrap_on_typing", GENERIC);

    PROPERTY_KIND_MAP.put("imports_layout", UNSUPPORTED);
    PROPERTY_KIND_MAP.put("packages_to_use_import_on_demand", UNSUPPORTED);
  }

  @NotNull
  public static EditorConfigPropertyKind getPropertyKind(@NotNull String name) {
    if (PROPERTY_KIND_MAP.containsKey(name)) {
      return PROPERTY_KIND_MAP.get(name);
    }
    return LANGUAGE;
  }

  private static void collectCommonLanguageProperties() {
    AbstractCodeStylePropertyMapper mapper = new AbstractCodeStylePropertyMapper(new CodeStyleSettings()) {
      @NotNull
      @Override
      protected List<CodeStyleObjectDescriptor> getSupportedFields() {
        return Collections.singletonList(new CodeStyleObjectDescriptor(new CommonCodeStyleSettings(Language.ANY), null));
      }

      @NotNull
      @Override
      public String getLanguageDomainId() {
        return "any";
      }

      @Nullable
      @Override
      public String getPropertyDescription(@NotNull String externalName) {
        return null;
      }
    };
    for (String property : mapper.enumProperties()) {
      PROPERTY_KIND_MAP.put(property, COMMON);
    }
  }
}