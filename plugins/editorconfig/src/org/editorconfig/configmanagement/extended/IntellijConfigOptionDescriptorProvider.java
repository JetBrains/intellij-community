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

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    List<EditorConfigOptionDescriptor> descriptors = ContainerUtil.newArrayList();
    List<AbstractCodeStylePropertyMapper> mappers = ContainerUtil.newArrayList();
    CodeStylePropertiesUtil.collectMappers(CodeStyle.getDefaultSettings(), mapper -> mappers.add(mapper));
    Map<String, EditorConfigDescriptor> propertyMap = ContainerUtil.newHashMap();
    for (AbstractCodeStylePropertyMapper mapper : mappers) {
      for (String property : mapper.enumProperties()) {
        List<String> ecNames = EditorConfigIntellijNameUtil.toEditorConfigNames(mapper, property);
        final EditorConfigDescriptor descriptor = createValueDescriptor(property, mapper);
        for (String ecName : ecNames) {
          propertyMap.put(ecName, descriptor);
        }
      }
    }
    for (String property: propertyMap.keySet()) {
      EditorConfigOptionDescriptor descriptor = new EditorConfigOptionDescriptor(
        new EditorConfigConstantDescriptor(property, null, null),
        propertyMap.get(property),
        null, null);
      descriptors.add(descriptor);
    }
    return descriptors;
  }

  @NotNull
  private static EditorConfigDescriptor createValueDescriptor(@NotNull String property, @NotNull AbstractCodeStylePropertyMapper mapper) {
    CodeStylePropertyAccessor accessor = mapper.getAccessor(property);
    if (accessor instanceof CodeStyleChoiceList) {
      return new EditorConfigUnionDescriptor(choicesToDescriptorList((CodeStyleChoiceList)accessor), null, null);
    }
    else if (accessor instanceof IntegerAccessor) {
      return new EditorConfigNumberDescriptor(null,  null);
    }
    return new EditorConfigStringDescriptor(null, null);
  }

  private static List<EditorConfigDescriptor> choicesToDescriptorList(@NotNull CodeStyleChoiceList list) {
    return ContainerUtil.map(list.getChoices(), s -> new EditorConfigConstantDescriptor(s, null, null));
  }


}
