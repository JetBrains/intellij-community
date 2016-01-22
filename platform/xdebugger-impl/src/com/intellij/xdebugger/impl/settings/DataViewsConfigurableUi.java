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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DataViewsConfigurableUi {
  public static final String DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY = "debugger.valueTooltipAutoShow";
  public static final String DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY = "debugger.valueTooltipAutoShowOnSelection";

  private JCheckBox enableAutoExpressionsCheckBox;
  private JFormattedTextField valueTooltipDelayTextField;
  private JPanel panel;
  private JCheckBox sortAlphabeticallyCheckBox;
  private JPanel myEditorSettingsPanel;
  private JCheckBox myShowValuesInlineCheckBox;
  private JCheckBox myShowValueTooltipCheckBox;
  private JCheckBox myShowValueTooltipOnCheckBox;
  private JBLabel myTooltipLabel;

  public DataViewsConfigurableUi() {
    UIUtil.configureNumericFormattedTextField(valueTooltipDelayTextField);
  }

  private int getValueTooltipDelay() {
    Object value = valueTooltipDelayTextField.getValue();
    return value instanceof Number ? ((Number)value).intValue() :
           StringUtilRt.parseInt((String)value, XDebuggerDataViewSettings.DEFAULT_VALUE_TOOLTIP_DELAY);
  }

  @NotNull
  public JComponent getComponent() {
    return panel;
  }

  public boolean isModified(@NotNull XDebuggerDataViewSettings settings) {
    return getValueTooltipDelay() != settings.getValueLookupDelay() ||
           sortAlphabeticallyCheckBox.isSelected() != settings.isSortValues() ||
           enableAutoExpressionsCheckBox.isSelected() != settings.isAutoExpressions() ||
           myShowValuesInlineCheckBox.isSelected() != settings.isShowValuesInline() ||
           myShowValueTooltipCheckBox.isSelected() != Registry.is(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY) ||
           myShowValueTooltipOnCheckBox.isSelected() != Registry.is(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY);
  }

  public void reset(@NotNull XDebuggerDataViewSettings settings) {
    valueTooltipDelayTextField.setValue(settings.getValueLookupDelay());
    sortAlphabeticallyCheckBox.setSelected(settings.isSortValues());
    enableAutoExpressionsCheckBox.setSelected(settings.isAutoExpressions());
    myShowValuesInlineCheckBox.setSelected(settings.isShowValuesInline());
    myShowValueTooltipCheckBox.setSelected(Registry.is(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY));
    myShowValueTooltipOnCheckBox.setSelected(Registry.is(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY));
    myTooltipLabel.setText(XDebuggerBundle.message("settings.tooltip.label", Registry.stringValue("ide.forcedShowTooltip")));
  }

  public void apply(@NotNull XDebuggerDataViewSettings settings) {
    settings.setValueLookupDelay(getValueTooltipDelay());
    settings.setSortValues(sortAlphabeticallyCheckBox.isSelected());
    settings.setAutoExpressions(enableAutoExpressionsCheckBox.isSelected());
    settings.setShowValuesInline(myShowValuesInlineCheckBox.isSelected());
    Registry.get(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY).setValue(myShowValueTooltipCheckBox.isSelected());
    Registry.get(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY).setValue(myShowValueTooltipOnCheckBox.isSelected());
  }

  private void createUIComponents() {
    myEditorSettingsPanel = new JPanel();
    IdeaTitledBorder titledBorder = IdeBorderFactory.createTitledBorder("Editor", false);
    myEditorSettingsPanel.setBorder(titledBorder);
    titledBorder.acceptMinimumSize(myEditorSettingsPanel);
  }
}