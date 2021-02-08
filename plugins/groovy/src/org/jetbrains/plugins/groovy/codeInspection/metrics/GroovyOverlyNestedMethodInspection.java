// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class GroovyOverlyNestedMethodInspection extends GroovyOverlyNestedMethodInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(GroovyBundle.message("overly.nested.method.nesting.limit.option"), this, "m_limit");
  }
}
