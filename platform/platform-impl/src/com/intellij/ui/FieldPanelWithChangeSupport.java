// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.options.BaseConfigurableWithChangeSupport;

public final class FieldPanelWithChangeSupport {
  public static AbstractFieldPanel createPanel(AbstractFieldPanel panel, final BaseConfigurableWithChangeSupport configurable) {
    panel.setChangeListener(() -> configurable.fireStateChanged());
    return panel;
  }

}
