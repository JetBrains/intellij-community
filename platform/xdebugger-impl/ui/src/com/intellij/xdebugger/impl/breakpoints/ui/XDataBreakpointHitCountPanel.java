// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.platform.debugger.impl.rpc.XDataBreakpointInfo;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.XDataBreakpointHitCountMode;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JList;

/**
 * Mirrors CidrBreakpointHitCountPanel's role in the monolith: filled into the "custom right properties"
 * wrapper (see CidrWatchpointType.createCustomRightPropertiesPanel), which sits lower in the dialog grid,
 * to the right of the Actions / Master breakpoint sections.
 */
@ApiStatus.Internal
public class XDataBreakpointHitCountPanel extends XBreakpointPropertiesSubPanel {
  private final JBCheckBox myEnabledCheckbox = new JBCheckBox(XDebuggerBundle.message("xbreakpoints.data.hitCount.checkbox"));
  private final ComboBox<XDataBreakpointHitCountMode> myModeCombo =
    new ComboBox<>(new EnumComboBoxModel<>(XDataBreakpointHitCountMode.class));
  private final IntegerField myValueField = new IntegerField(null, 1, Integer.MAX_VALUE);
  private final JComponent myComponent;
  // The loaded value, kept as-is while the hit count is disabled. myValueField always shows at
  // least 1, so its own value cannot stand in for a disabled hit count's stored value (often 0).
  private int myLoadedHitCountValue;

  public XDataBreakpointHitCountPanel() {
    myModeCombo.setRenderer(new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends XDataBreakpointHitCountMode> list, @Nullable XDataBreakpointHitCountMode value,
                             int index, boolean selected, boolean hasFocus) {
        setText(value == null ? "" : getHitCountModeText(value));
      }
    });
    myEnabledCheckbox.addActionListener(e -> updateEnabledState());
    DebuggerUIUtil.focusEditorOnCheck(myEnabledCheckbox, myValueField);

    myComponent = FormBuilder.createFormBuilder()
      .addComponent(myEnabledCheckbox)
      .addComponent(myModeCombo)
      .addComponent(myValueField)
      .getPanel();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  void loadProperties() {
    XDataBreakpointInfo info = myBreakpoint.getDataBreakpointInfo();
    if (info == null) return;

    myEnabledCheckbox.setSelected(info.getHitCountEnabled());
    myModeCombo.setSelectedItem(info.getHitCountMode());
    myLoadedHitCountValue = info.getHitCountValue();
    myValueField.setValue(myLoadedHitCountValue > 0 ? myLoadedHitCountValue : 1);
    updateEnabledState();
  }

  @Override
  void saveProperties() {
    XDataBreakpointHitCountMode mode = (XDataBreakpointHitCountMode)myModeCombo.getSelectedItem();
    if (mode == null) {
      // setDataBreakpointHitCount() takes a non-null mode; the combo is never emptied in practice, but this
      // guards the Kotlin call site against a null Object cast.
      mode = XDataBreakpointHitCountMode.EQUAL_TO;
    }
    boolean enabled = myEnabledCheckbox.isSelected();
    // Disabled leaves myValueField at its display default (at least 1); send back the loaded value instead
    // so a save that never touched this panel does not report a spurious change and re-register the breakpoint.
    int value = enabled ? myValueField.getValue() : myLoadedHitCountValue;
    myBreakpoint.setDataBreakpointHitCount(enabled, mode, value);
  }

  private void updateEnabledState() {
    boolean enabled = myEnabledCheckbox.isSelected();
    myModeCombo.setEnabled(enabled);
    myValueField.setEnabled(enabled);
  }

  private static @NotNull String getHitCountModeText(@NotNull XDataBreakpointHitCountMode mode) {
    return switch (mode) {
      case EQUAL_TO -> XDebuggerBundle.message("xbreakpoints.data.hitCount.mode.equalTo");
      case GREATER_THAN_OR_EQUAL_TO -> XDebuggerBundle.message("xbreakpoints.data.hitCount.mode.greaterThanOrEqualTo");
      case MULTIPLE_OF -> XDebuggerBundle.message("xbreakpoints.data.hitCount.mode.multipleOf");
    };
  }
}
