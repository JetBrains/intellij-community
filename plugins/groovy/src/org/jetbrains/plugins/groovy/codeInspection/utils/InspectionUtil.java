// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.utils;

import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;

import javax.swing.*;

public final class InspectionUtil {
  public static JComponent createSingleIntegerFieldOptionsPanel(BaseInspection inspection, String fieldName, String configurationLabel) {
    return new SingleIntegerFieldOptionsPanel(configurationLabel, inspection, fieldName);
  }
}
