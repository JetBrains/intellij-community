// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IntellijConfigOptionDescriptorProvider implements EditorConfigOptionDescriptorProvider {

  /**
   * Properties not supported currently in EditorConfig
   */
  private final static Set<String> UNSUPPORTED_PROPERTIES = ContainerUtil.newHashSet();
  static {
    UNSUPPORTED_PROPERTIES.add("imports_layout");
    UNSUPPORTED_PROPERTIES.add("packages_to_use_import_on_demand");
  }

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
    List<EditorConfigOptionDescriptor> descriptors = ContainerUtil.newArrayList();
    List<AbstractCodeStylePropertyMapper> mappers = ContainerUtil.newArrayList();
    CodeStylePropertiesUtil.collectMappers(CodeStyle.getDefaultSettings(), mapper -> mappers.add(mapper));
    for (AbstractCodeStylePropertyMapper mapper : mappers) {
      for (String property : mapper.enumProperties()) {
        if (UNSUPPORTED_PROPERTIES.contains(property)) continue;
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
    else if (accessor instanceof IntegerAccessor) {
      return new EditorConfigNumberDescriptor(null,  null);
    }
    else if (accessor instanceof ValueListPropertyAccessor) {
      return new EditorConfigListDescriptor(0, true, Collections.singletonList(new EditorConfigStringDescriptor(null, null)), null,  null);
    }
    else if (accessor instanceof ExternalStringAccessor) {
      return new EditorConfigStringDescriptor(null, null);
    }
    return null;
  }

  private static List<EditorConfigDescriptor> choicesToDescriptorList(@NotNull CodeStyleChoiceList list) {
    return ContainerUtil.map(list.getChoices(), s -> new EditorConfigConstantDescriptor(s, null, null));
  }


}
