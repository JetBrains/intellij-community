/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.List;
import java.util.stream.Collectors;
/**
 * @author Konstantin Bulenkov
 */
public class ShowUIDefaultsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final UIDefaults defaults = UIManager.getDefaults();
    Enumeration keys = defaults.keys();
    final Object[][] data = new Object[defaults.size()][2];
    int i = 0;
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      data[i][0] = key;
      data[i][1] = defaults.get(key);
      i++;
    }

    Arrays.sort(data, (o1, o2) -> StringUtil.naturalCompare(o1[0 ].toString(), o2[0].toString()));

    final Project project = getEventProject(e);
    new DialogWrapper(project) {
      {
        setTitle("Edit LaF Defaults");
        setModal(false);
        init();
      }

      public JBTable myTable;

      @Nullable
      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTable;
      }

      @Nullable
      @Override
      protected String getDimensionServiceKey() {
        return project == null ? null : "UI.Defaults.Dialog";
      }

      @Override
      protected JComponent createCenterPanel() {
        final JBTable table = new JBTable(new DefaultTableModel(data, new Object[]{"Name", "Value"}) {
          @Override
          public boolean isCellEditable(int row, int column) {
            Object value = getValueAt(row, column);
            return column == 1 && (value instanceof Color ||
                                   value instanceof Integer ||
                                   value instanceof Border ||
                                   value instanceof UIUtil.GrayFilter ||
                                   value instanceof Font);
          }
        }) {
          @Override
          public boolean editCellAt(int row, int column, EventObject e) {
            if (isCellEditable(row, column) && e instanceof MouseEvent) {
              Object key = getValueAt(row, 0);
              Object value = getValueAt(row, column);

              if (value instanceof Color) {
                Color newColor = ColorPicker.showDialog(this, "Choose Color", (Color)value, true, null, true);
                if (newColor != null) {
                  final ColorUIResource colorUIResource = new ColorUIResource(newColor);

                  // MultiUIDefaults overrides remove but does not override put.
                  // So to avoid duplications we should first remove the value and then put it again.
                  UIManager.getDefaults().remove(key);
                  UIManager.getDefaults().put(key, colorUIResource);
                  setValueAt(colorUIResource, row, column);
                }
              } else if (value instanceof Integer) {
                Integer newValue = editNumber(key.toString(), value.toString());
                if (newValue != null) {
                  UIManager.getDefaults().remove(key);
                  UIManager.getDefaults().put(key, newValue);
                  setValueAt(newValue, row, column);
                }
              } else if (value instanceof Border) {
                Insets i = ((Border)value).getBorderInsets(null);
                String oldBorder = String.format("%d,%d,%d,%d", i.top, i.left, i.bottom, i.right);
                Border newValue = editBorder(key.toString(), oldBorder);
                if (newValue != null) {
                  UIManager.getDefaults().remove(key);
                  UIManager.getDefaults().put(key, newValue);
                  setValueAt(newValue, row, column);
                }
              } else if (value instanceof UIUtil.GrayFilter) {
                UIUtil.GrayFilter f = (UIUtil.GrayFilter)value;
                String oldFilter = String.format("%d,%d,%d", f.getBrightness(), f.getContrast(), f.getAlpha());
                UIUtil.GrayFilter newValue = editGrayFilter(key.toString(), oldFilter);
                if (newValue != null) {
                  UIManager.getDefaults().remove(key);
                  UIManager.getDefaults().put(key, newValue);
                  setValueAt(newValue, row, column);
                }
              } else if (value instanceof Font) {
                Font newValue = editFontSize(key.toString(), (Font)value);
                if (newValue != null) {
                  UIManager.getDefaults().remove(key);
                  UIManager.getDefaults().put(key, newValue);
                  setValueAt(newValue, row, column);
                }
              }
            }
            return false;
          }
        };
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(JTable table,
                                                         Object value,
                                                         boolean isSelected,
                                                         boolean hasFocus,
                                                         int row,
                                                         int column) {
            final JPanel panel = new JPanel(new BorderLayout());
            final JLabel label = new JLabel(value == null ? "" : value.toString());
            panel.add(label, BorderLayout.CENTER);
            if (value instanceof Color) {
              final Color c = (Color)value;
              label.setText(String.format("[r=%d,g=%d,b=%d] hex=0x%s", c.getRed(), c.getGreen(), c.getBlue(), ColorUtil.toHex(c)));
              label.setForeground(ColorUtil.isDark(c) ? JBColor.white : JBColor.black);
              panel.setBackground(c);
              return panel;
            } else if (value instanceof Icon) {
              try {
                final Icon icon = new IconWrap((Icon)value);
                if (icon.getIconHeight() <= 20) {
                  label.setIcon(icon);
                }
                label.setText(String.format("(%dx%d) %s)",icon.getIconWidth(), icon.getIconHeight(), label.getText()));
              }
              catch (Throwable e1) {//
              }
              return panel;
            } else if (value instanceof Border) {
              try {
                final Insets i = ((Border)value).getBorderInsets(null);
                label.setText(String.format("[%d, %d, %d, %d] %s", i.top, i.left, i.bottom, i.right, label.getText()));
                return panel;
              } catch (Exception ignore) {}
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          }
        });
        final JBScrollPane pane = new JBScrollPane(table);
        new TableSpeedSearch(table, (o, cell) -> cell.column == 1 ? null : String.valueOf(o));
        table.setShowGrid(false);
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(pane, BorderLayout.CENTER);
        myTable = table;
        TableUtil.ensureSelectionExists(myTable);
        return panel;
      }

      private @Nullable Integer editNumber(String key, String value) {
        String newValue = Messages.showInputDialog(getRootPane(), "Enter new value for " + key, "Number Editor", null, value,
                                   new InputValidator() {
                                     @Override
                                     public boolean checkInput(String inputString) {
                                       try {
                                         Integer.parseInt(inputString);
                                         return true;
                                       } catch (NumberFormatException nfe){
                                         return false;
                                       }
                                     }

                                     @Override
                                     public boolean canClose(String inputString) {
                                       return checkInput(inputString);
                                     }
                                   });

        return newValue != null ? Integer.valueOf(newValue) : null;
      }

      @Nullable
      private Border editBorder(String key, String value) {
        String newValue = Messages.showInputDialog(getRootPane(),
           "Enter new value for " + key + "\nin form top,left,bottom,right",
           "Border Editor", null, value,
           new InputValidator() {
             @Override
             public boolean checkInput(String inputString) {
               return parseBorder(inputString) != null;
             }

             @Override
             public boolean canClose(String inputString) {
               return checkInput(inputString);
             }
           });

        return newValue != null ? parseBorder(newValue) : null;
      }

      @Nullable
      private Border parseBorder(String value) {
        String[] parts = value.split(",");
        if(parts.length != 4) {
          return null;
        }

        try {
          List<Integer> v = Arrays.stream(parts).map(p -> Integer.parseInt(p)).collect(Collectors.toList());
          return JBUI.Borders.empty(v.get(0), v.get(1), v.get(2), v.get(3));
        } catch (NumberFormatException nex) {
          return null;
        }
      }

      @Nullable
      private UIUtil.GrayFilter editGrayFilter(String key, String value) {
        String newValue = Messages.showInputDialog(getRootPane(),
                                                   "Enter new value for " + key + "\nin form brightness,contrast,alpha",
                                                   "Gray Filter Editor", null, value,
                                                   new InputValidator() {
                                                     @Override
                                                     public boolean checkInput(String inputString) {
                                                       return parseGrayFilter(inputString) != null;
                                                     }

                                                     @Override
                                                     public boolean canClose(String inputString) {
                                                       return checkInput(inputString);
                                                     }
                                                   });

        return newValue != null ? parseGrayFilter(newValue) : null;
      }

      @Nullable
      private UIUtil.GrayFilter parseGrayFilter(String value) {
        String[] parts = value.split(",");
        if(parts.length != 3) {
          return null;
        }

        try {
          List<Integer> v = Arrays.stream(parts).map(p -> Integer.parseInt(p)).collect(Collectors.toList());
          return new UIUtil.GrayFilter(v.get(0), v.get(1), v.get(2));
        } catch (NumberFormatException nex) {
          return null;
        }
      }

      @Nullable
      private Font editFontSize(String key, Font font) {
        String newValue = Messages.showInputDialog(getRootPane(),
                                                   "Enter new font size for " + key,
                                                   "Font Size Editor", null, Integer.toString(font.getSize()),
                                                   new InputValidator() {
                                                     @Override
                                                     public boolean checkInput(String inputString) {
                                                       return parseFontSize(font, inputString) != null;
                                                     }

                                                     @Override
                                                     public boolean canClose(String inputString) {
                                                       return checkInput(inputString);
                                                     }
                                                   });

        return newValue != null ? parseFontSize(font, newValue) : null;
      }

      @Nullable
      private Font parseFontSize(Font font, String value) {
        try {
          int newSize = Integer.parseInt(value);
          return (newSize > 0) ? font.deriveFont((float)newSize) : null;
        } catch (NumberFormatException nex) {
          return null;
        }
      }
    }.show();
  }

  private static class IconWrap implements Icon {
    private final Icon myIcon;

    public IconWrap(Icon icon) {
      myIcon = icon;
    }


    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      try {
        myIcon.paintIcon(c, g, x, y);
      } catch (Exception e) {
        EmptyIcon.ICON_0.paintIcon(c, g, x, y);
      }
    }

    @Override
    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }
}
