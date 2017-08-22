/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.panels.HorizontalLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class TestTextFieldAction extends DumbAwareAction {
  private JFrame frame;

  @Override
  public void actionPerformed(AnActionEvent event) {
    if (frame != null && frame.isVisible()) {
      frame.dispose();
      frame = null;
    }
    else {
      if (frame == null) {
        frame = new JFrame("Test Text Fields");
        frame.add(new View());
        frame.pack();
        frame.setLocationRelativeTo(null);
      }
      frame.setVisible(true);
      frame.toFront();
    }
  }

  private enum Fill {None, Both, Horizontal, Vertical}

  private static final class View extends JPanel {
    private final JCheckBox columns = new JCheckBox("20 columns");
    private final JCheckBox opaque = new JCheckBox("Opaque");
    private final JCheckBox gradient = new JCheckBox("Gradient");
    private final JComboBox fill = new ComboBox<>(Fill.values());
    private final JPanel control = new JPanel(new HorizontalLayout(5));
    private final JPanel center = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        if (g instanceof Graphics2D && gradient.isSelected()) {
          Graphics2D g2d = (Graphics2D)g;
          Rectangle bounds = new Rectangle(getWidth(), getHeight());
          g2d.setPaint(new LinearGradientPaint(
            bounds.x, bounds.y, bounds.width, bounds.height, new float[]{0, 1},
            new Color[]{JBColor.LIGHT_GRAY, JBColor.DARK_GRAY}));
          g2d.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        else {
          super.paintComponent(g);
        }
      }
    };
    private final List<JTextField> fields = Arrays.asList(
      new JBTextField(),
      new JBTextField() {{
        putClientProperty("JTextField.variant", "search");
      }},
      new SearchTextField(false).getTextEditor(),
      new SearchTextField(true).getTextEditor(),
      new ExpandableTextField(),
      new ExtendableTextField() {{
        setExtensions(
          new Extension() {
            @Override
            public Icon getIcon(boolean hovered) {
              return AllIcons.General.GearPlain;
            }

            @Override
            public String getTooltip() {
              return "Settings";
            }

            @Override
            public boolean isIconBeforeText() {
              return true;
            }
          },
          new Extension() {
            @Override
            public Icon getIcon(boolean hovered) {
              return hovered ? AllIcons.General.LocateHover : AllIcons.General.Locate;
            }

            @Override
            public String getTooltip() {
              return "Locate";
            }

            @Override
            public boolean isIconBeforeText() {
              return true;
            }

            @Override
            public Runnable getActionOnClick() {
              return null;
            }
          },
          new Extension() {
            @Override
            public Icon getIcon(boolean hovered) {
              return AllIcons.Actions.ForceRefresh;
            }

            @Override
            public String getTooltip() {
              return "Refresh";
            }
          },
          new Extension() {
            @Override
            public Icon getIcon(boolean hovered) {
              if (null == getActionOnClick()) return null;
              return hovered ? AllIcons.Actions.Clean : AllIcons.Actions.CleanLight;
            }

            @Override
            public String getTooltip() {
              return "Clear";
            }

            @Override
            public Runnable getActionOnClick() {
              return getText().isEmpty() ? null : () -> setText(null);
            }
          });
      }});

    private View() {
      super(new BorderLayout(10, 10));
      setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      add(BorderLayout.NORTH, control);
      add(BorderLayout.CENTER, center);

      control.add(columns);
      columns.setSelected(true);
      columns.addChangeListener(event -> updateColumns());
      updateColumns();

      control.add(opaque);
      opaque.addChangeListener(event -> updateOpaque());
      updateOpaque();

      control.add(gradient);
      gradient.addChangeListener(event -> updateGradient());
      updateGradient();

      control.add(new JLabel("Fill:"));
      control.add(fill);
      fill.addItemListener(event -> updateFill());
      updateFill();
    }

    private void updateColumns() {
      int amount = columns.isSelected() ? 20 : 0;
      update(field -> field.setColumns(amount));
    }

    private void updateOpaque() {
      boolean state = opaque.isSelected();
      update(field -> field.setOpaque(state));
    }

    private void updateGradient() {
      update(field -> {
      });
    }

    private void updateFill() {
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 1;
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.anchor = GridBagConstraints.CENTER;
      gbc.fill = fill.getSelectedIndex();
      if (gbc.fill < 0) gbc.fill = 0;
      center.removeAll();
      update(field -> center.add(field, gbc));
    }

    private void update(Consumer<JTextField> consumer) {
      fields.forEach(consumer);
      center.revalidate();
      center.repaint();
    }
  }
}
