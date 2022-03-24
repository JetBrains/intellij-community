// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;

public class GroovyOverlyNestedMethodInspection extends GroovyOverlyNestedMethodInspectionBase {

  @Override
  public JComponent createGroovyOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(GroovyBundle.message("overly.nested.method.nesting.limit.option"), this, "m_limit");
  }
}
