// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
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

public class DebuggerColorsPage implements ColorSettingsPage, DisplayPrioritySortable {
  @Override
  public @NotNull String getDisplayName() {
    return XDebuggerBundle.message("xdebugger.colors.page.name");
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Actions.StartDebugger;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return new AttributesDescriptor[] {
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.breakpoint.line"), DebuggerColors.BREAKPOINT_ATTRIBUTES),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.execution.point"), DebuggerColors.EXECUTIONPOINT_ATTRIBUTES),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.not.top.frame"), DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.evaluated.expression"), DebuggerColors.EVALUATED_EXPRESSION_ATTRIBUTES),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.evaluated.expression.execution.line"), DebuggerColors.EVALUATED_EXPRESSION_EXECUTION_LINE_ATTRIBUTES),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.inlined.values"), DebuggerColors.INLINED_VALUES),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.inlined.values.modified"), DebuggerColors.INLINED_VALUES_MODIFIED),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.inlined.values.execution.line"), DebuggerColors.INLINED_VALUES_EXECUTION_LINE),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.smart.step.into.target"), DebuggerColors.SMART_STEP_INTO_TARGET),
      new AttributesDescriptor(XDebuggerBundle.message("options.java.attribute.descriptor.smart.step.into.selection"), DebuggerColors.SMART_STEP_INTO_SELECTION),
      new AttributesDescriptor(XDebuggerBundle.message("options.kotlin.attribute.descriptor.inline.stack.frames"), DebuggerColors.INLINE_STACK_FRAMES)
    };
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @Override
  public @NonNls @NotNull String getDemoText() {
    return " ";
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }
}
