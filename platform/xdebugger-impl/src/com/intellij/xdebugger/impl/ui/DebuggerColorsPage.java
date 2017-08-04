/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author max
 */
public class DebuggerColorsPage implements ColorSettingsPage, DisplayPrioritySortable {
  @Override
  @NotNull
  public String getDisplayName() {
    return XDebuggerBundle.message("xdebugger.colors.page.name");
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return AllIcons.Actions.StartDebugger;
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[] {
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.breakpoint.line"), DebuggerColors.BREAKPOINT_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.execution.point"), DebuggerColors.EXECUTIONPOINT_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.not.top.frame"), DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.evaluated.expression"), DebuggerColors.EVALUATED_EXPRESSION_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.evaluated.expression.execution.line"), DebuggerColors.EVALUATED_EXPRESSION_EXECUTION_LINE_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.inlined.values"), DebuggerColors.INLINED_VALUES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.inlined.values.modified"), DebuggerColors.INLINED_VALUES_MODIFIED),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.inlined.values.execution.line"), DebuggerColors.INLINED_VALUES_EXECUTION_LINE),
    };
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[] {
      new ColorDescriptor(OptionsBundle.message("options.java.attribute.descriptor.recursive.call"), DebuggerColors.RECURSIVE_CALL_ATTRIBUTES, ColorDescriptor.Kind.BACKGROUND)
    };
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @Override
  @NonNls
  @NotNull
  public String getDemoText() {
    return " ";
  }

  @Override
  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }
}
