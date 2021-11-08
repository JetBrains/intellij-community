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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IntellijConfigOptionDescriptorProvider implements EditorConfigOptionDescriptorProvider {

  private final static String EXCEPT_NONE_REGEXP = "(^(?!none).*|.{4}.+)";

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
        List<String> ecNames = getEditorConfigNames(mapper, property);
        if (ecNames.isEmpty()) continue;
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

  private static List<String> getEditorConfigNames(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String property) {
    if (EditorConfigIntellijNameUtil.isIndentProperty(property) && !(mapper instanceof GeneralCodeStylePropertyMapper)) {
      // Create a special language indent property like ij_lang_indent_size
      return Collections.singletonList(EditorConfigIntellijNameUtil.getLanguageProperty(mapper, property));
    }
    if (IntellijPropertyKindMap.getPropertyKind(property) == EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD) {
      // Descriptions for other standard properties are added separately
      return Collections.emptyList();
    }
    return EditorConfigIntellijNameUtil.toEditorConfigNames(mapper, property);
  }

  @Nullable
  private static EditorConfigDescriptor createValueDescriptor(@NotNull String property, @NotNull AbstractCodeStylePropertyMapper mapper) {
    CodeStylePropertyAccessor<?> accessor = mapper.getAccessor(property);
    if (accessor instanceof CodeStyleChoiceList) {
      return new EditorConfigUnionDescriptor(choicesToDescriptorList((CodeStyleChoiceList)accessor), null, null);
    }
    else if (accessor instanceof IntegerAccessor) {
      return new EditorConfigNumberDescriptor(null,  null);
    }
    else if (accessor instanceof ValueListPropertyAccessor) {
      return createListDescriptor(new EditorConfigStringDescriptor(null, null, EXCEPT_NONE_REGEXP),
                                  ((ValueListPropertyAccessor<?>)accessor).isEmptyListAllowed());
    }
    else if (accessor instanceof ExternalStringAccessor) {
      return new EditorConfigStringDescriptor(null, null, ".*");
    }
    else if (accessor instanceof VisualGuidesAccessor) {
      return createListDescriptor(new EditorConfigNumberDescriptor(null, null), true);
    }
    return null;
  }

  @NotNull
  private static EditorConfigDescriptor createListDescriptor(@NotNull EditorConfigDescriptor childDescriptor, boolean canBeEmpty) {
    final EditorConfigListDescriptor listDescriptor =
      new EditorConfigListDescriptor(0, true, Collections.singletonList(childDescriptor), null, null);
    if (canBeEmpty) {
      return new EditorConfigUnionDescriptor(
        Arrays.asList(
          listDescriptor,
          new EditorConfigConstantDescriptor(EditorConfigValueUtil.EMPTY_LIST_VALUE, null, null)),
        null, null);
    }
    else {
      return listDescriptor;
    }
  }

  private static List<EditorConfigDescriptor> choicesToDescriptorList(@NotNull CodeStyleChoiceList list) {
    return ContainerUtil.map(list.getChoices(), s -> new EditorConfigConstantDescriptor(s, null, null));
  }


}
