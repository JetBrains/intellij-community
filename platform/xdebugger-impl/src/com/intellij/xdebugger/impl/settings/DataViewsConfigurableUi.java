/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DataViewsConfigurableUi {
  private JCheckBox enableAutoExpressionsCheckBox;
  private JFormattedTextField valueTooltipDelayTextField;
  private JPanel panel;
  private JCheckBox sortAlphabeticallyCheckBox;

  public DataViewsConfigurableUi() {
    UIUtil.configureNumericFormattedTextField(valueTooltipDelayTextField);
  }

  private int getValueTooltipDelay() {
    Object value = valueTooltipDelayTextField.getValue();
    return value instanceof Number ? ((Number)value).intValue() : StringUtilRt.parseInt((String)value, XDebuggerDataViewSettings.DEFAULT_VALUE_TOOLTIP_DELAY);
  }

  @NotNull
  public JComponent getComponent() {
    return panel;
  }

  public boolean isModified(@NotNull XDebuggerDataViewSettings settings) {
    return getValueTooltipDelay() != settings.getValueLookupDelay() ||
           sortAlphabeticallyCheckBox.isSelected() != settings.isSortValues() ||
           enableAutoExpressionsCheckBox.isSelected() != settings.isAutoExpressions();
  }

  public void reset(@NotNull XDebuggerDataViewSettings settings) {
    valueTooltipDelayTextField.setValue(settings.getValueLookupDelay());
    sortAlphabeticallyCheckBox.setSelected(settings.isSortValues());
    enableAutoExpressionsCheckBox.setSelected(settings.isAutoExpressions());
  }

  public void apply(@NotNull XDebuggerDataViewSettings settings) {
    settings.setValueLookupDelay(getValueTooltipDelay());
    settings.setSortValues(sortAlphabeticallyCheckBox.isSelected());
    settings.setAutoExpressions(enableAutoExpressionsCheckBox.isSelected());
  }
}