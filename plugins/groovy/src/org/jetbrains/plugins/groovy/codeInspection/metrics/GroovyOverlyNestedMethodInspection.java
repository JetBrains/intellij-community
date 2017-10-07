// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.plugins.groovy.codeInspection.utils.InspectionUtil;

import javax.swing.*;

public class GroovyOverlyNestedMethodInspection extends GroovyOverlyNestedMethodInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    return InspectionUtil.createSingleIntegerFieldOptionsPanel(this, "m_limit", "Maximum nesting depth:");
  }
}