/*
 * Copyright 2003-2007 Dave Griffith
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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.ig.BaseInspection;

import javax.swing.*;

public abstract class ClassMetricInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public int m_limit = getDefaultLimit();

  protected abstract int getDefaultLimit();

  protected abstract String getConfigurationLabel();

  protected int getLimit() {
    return m_limit;
  }

  @Override
  public JComponent createOptionsPanel() {
    final String label = getConfigurationLabel();
    return new SingleIntegerFieldOptionsPanel(label,
                                              this, "m_limit");
  }
}