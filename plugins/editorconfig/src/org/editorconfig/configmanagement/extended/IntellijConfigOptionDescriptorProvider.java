// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.properties.*;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.Utils;
import org.editorconfig.language.extensions.EditorConfigOptionDescriptorProvider;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;
import org.editorconfig.language.schema.descriptors.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IntellijConfigOptionDescriptorProvider implements EditorConfigOptionDescriptorProvider {

  @NotNull
  @Override
  public List<EditorConfigOptionDescriptor> getOptionDescriptors() {
    if (!Utils.isFullIntellijSettingsSupport()) {
      return Collections.emptyList();
    }
    return getAllOptions();
  }

  @Override
  public boolean requiresFullSupport() {
    return Utils.isFullIntellijSettingsSupport();
  }

  private static List<EditorConfigOptionDescriptor> getAllOptions() {
    List<EditorConfigOptionDescriptor> descriptors = new ArrayList<>();
    List<AbstractCodeStylePropertyMapper> mappers = new ArrayList<>();
    CodeStylePropertiesUtil.collectMappers(CodeStyle.getDefaultSettings(), mapper -> mappers.add(mapper));
    for (AbstractCodeStylePropertyMapper mapper : mappers) {
      for (String property : mapper.enumProperties()) {
        if (IntellijPropertyKindMap.getPropertyKind(property) == EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD) {
          // Descriptions for standard properties are added separately
          continue;
        }
        List<String> ecNames = EditorConfigIntellijNameUtil.toEditorConfigNames(mapper, property);
        final EditorConfigDescriptor valueDescriptor = createValueDescriptor(property, mapper);
        if (valueDescriptor != null) {
          for (String ecName : ecNames) {
            EditorConfigOptionDescriptor descriptor = new EditorConfigOptionDescriptor(
              new EditorConfigConstantDescriptor(ecName, mapper.getPropertyDescription(property), null),
              valueDescriptor,
              null, null);
            descriptors.add(descriptor);
          }
        }
      }
    }
    return descriptors;
  }

  @Nullable
  private static EditorConfigDescriptor createValueDescriptor(@NotNull String property, @NotNull AbstractCodeStylePropertyMapper mapper) {
    CodeStylePropertyAccessor accessor = mapper.getAccessor(property);
    if (accessor instanceof CodeStyleChoiceList) {
      return new EditorConfigUnionDescriptor(choicesToDescriptorList((CodeStyleChoiceList)accessor), null, null);
    }
    else if (isFormatterOnOffTag(accessor)) {
      return new EditorConfigPairDescriptor(
        new EditorConfigStringDescriptor(null, null, ".*"),
        new EditorConfigStringDescriptor(null, null, ".*"),
        null, null);
    }
    else if (accessor instanceof IntegerAccessor) {
      return new EditorConfigNumberDescriptor(null,  null);
    }
    else if (accessor instanceof ValueListPropertyAccessor) {
      return new EditorConfigListDescriptor(0, true, Collections.singletonList(new EditorConfigStringDescriptor(null, null, ".*")), null,  null);
    }
    else if (accessor instanceof ExternalStringAccessor) {
      return new EditorConfigStringDescriptor(null, null, ".*");
    }
    else if (accessor instanceof VisualGuidesAccessor) {
      return new EditorConfigListDescriptor(0, true, Collections.singletonList(new EditorConfigNumberDescriptor(null, null)), null,  null);
    }
    return null;
  }

  private static boolean isFormatterOnOffTag(@NotNull CodeStylePropertyAccessor accessor) {
    String name = accessor.getPropertyName();
    return "formatter_on_tag".equals(name) || "formatter_off_tag".equals(name);
  }

  private static List<EditorConfigDescriptor> choicesToDescriptorList(@NotNull CodeStyleChoiceList list) {
    return ContainerUtil.map(list.getChoices(), s -> new EditorConfigConstantDescriptor(s, null, null));
  }


}
