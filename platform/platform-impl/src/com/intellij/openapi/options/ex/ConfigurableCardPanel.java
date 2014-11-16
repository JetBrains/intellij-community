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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.GradientViewport;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

/**
 * @author Sergey.Malenkov
 */
public class ConfigurableCardPanel extends CardLayoutPanel<Configurable, JComponent> {
  @Override
  protected JComponent create(final Configurable configurable) {
    return configurable == null ? null : ApplicationManager.getApplication().runReadAction(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        JComponent component = configurable.createComponent();
        if (component != null) {
          configurable.reset();
          if (ConfigurableWrapper.cast(MasterDetails.class, configurable) == null) {
            if (ConfigurableWrapper.cast(Configurable.NoMargin.class, configurable) == null) {
              component.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            }
            if (ConfigurableWrapper.cast(Configurable.NoScroll.class, configurable) == null) {
              JScrollPane scroll = ScrollPaneFactory.createScrollPane(null, true);
              scroll.setViewport(new GradientViewport(component, 5, 0, 0, 0, true));
              scroll.getVerticalScrollBar().setUnitIncrement(10);
              component = scroll;
            }
          }
        }
        return component;
      }
    });
  }

  @Override
  protected void dispose(Configurable configurable) {
    if (configurable != null) {
      configurable.disposeUIResources();
    }
  }
}
