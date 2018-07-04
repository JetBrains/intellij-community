/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.application.options.colors.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VcsColorsPageFactory implements ColorAndFontPanelFactory, ColorAndFontDescriptorsProvider, DisplayPrioritySortable {
  public static final String VCS_GROUP = ApplicationBundle.message("title.vcs");

  @Override
  @NotNull
  public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
    final SchemesPanel schemesPanel = new SchemesPanel(options);
    final OptionsPanelImpl optionsPanel = new OptionsPanelImpl(options, schemesPanel, VCS_GROUP);
    final VcsPreviewPanel previewPanel = new VcsPreviewPanel();

    schemesPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void schemeChanged(final Object source) {
        previewPanel.setColorScheme(options.getSelectedScheme());
        optionsPanel.updateOptionsList();
      }
    });

    return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, VCS_GROUP, null, null);
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[0];
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    List<ColorDescriptor> descriptors = new ArrayList<>();

    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.added.lines"), EditorColors.ADDED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND));
    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.modified.lines"), EditorColors.MODIFIED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND));
    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.deleted.lines"), EditorColors.DELETED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND));
    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.whitespaces.modified.lines"), EditorColors.WHITESPACES_MODIFIED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND));
    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.border.lines"), EditorColors.BORDER_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND));

    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.annotations"), EditorColors.ANNOTATIONS_COLOR, ColorDescriptor.Kind.FOREGROUND));

    List<ColorKey> colorKeys = AnnotationsSettings.ANCHOR_COLOR_KEYS;
    for (int i = 0; i < colorKeys.size(); i++) {
      descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.annotations.color.n", i + 1), colorKeys.get(i), ColorDescriptor.Kind.BACKGROUND));
    }

    return ArrayUtil.toObjectArray(descriptors, ColorDescriptor.class);
  }

  @NotNull
  @Override
  public String getPanelDisplayName() {
    return VCS_GROUP;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return VCS_GROUP;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }
}
