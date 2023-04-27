// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TextCopyProvider;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.picker.ColorListener;
import com.intellij.ui.speedSearch.FilteringTableModel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static com.intellij.util.ui.JBUI.Panels.simplePanel;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("HardCodedStringLiteral")
public class ShowUIDefaultsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    perform(project);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public void perform(Project project) {
    new DialogWrapper(project, true) {
      {
        setTitle(IdeBundle.message("dialog.title.edit.laf.defaults"));
        setModal(false);
        init();
      }

      public JBTable myTable;
      public JBTextField mySearchField;
      private static final String SEARCH_FIELD_HISTORY_KEY = "LaFDialog.filter";
      private static final String COLORS_ONLY_KEY = "LaFDialog.ColorsOnly";
      private static final String LAST_SELECTED_KEY = "LaFDialog.lastSelectedElement";
      public JBCheckBox myColorsOnly;

      @Override
      protected void doOKAction() {
        super.doOKAction();
        LafManager.getInstance().updateUI();
        PropertiesComponent.getInstance().setValue(SEARCH_FIELD_HISTORY_KEY, mySearchField.getText());
        Object selected = myTable.getValueAt(myTable.getSelectedRow(), 0);
        if (selected instanceof Pair) {
          PropertiesComponent.getInstance().setValue(LAST_SELECTED_KEY, ((Pair<?, ?>)selected).first.toString());
        }
      }

      @Nullable
      @Override
      public JComponent getPreferredFocusedComponent() {
        return mySearchField;
      }

      @Nullable
      @Override
      protected String getDimensionServiceKey() {
        return project == null ? null : "UI.Defaults.Dialog";
      }

      @Override
      protected JComponent createCenterPanel() {
        mySearchField = new JBTextField(40);
        mySearchField.setText(PropertiesComponent.getInstance().getValue(SEARCH_FIELD_HISTORY_KEY, ""));
        JPanel top = UI.PanelFactory.panel(mySearchField).withLabel(IdeBundle.message("label.ui.filter")).createPanel();
        final JBTable table = new JBTable(createFilteringModel()) {
          @Override
          public boolean editCellAt(int row, int column, EventObject e) {
            if (isCellEditable(row, column) && e instanceof MouseEvent) {
              var pair = (Pair<?, ?>)getValueAt(row, 0);
              Object key = pair.first;
              Object value = pair.second;
              final Ref<Boolean> changed = Ref.create(false);

              if (value instanceof Color) {
                ColorChooserService.getInstance().showPopup(null, (Color)value, new ColorListener() {
                  @Override
                  public void colorChanged(Color color, Object source) {
                    if (color != null) {
                      final ColorUIResource colorUIResource = new ColorUIResource(color);

                      // MultiUIDefaults overrides remove but does not override put.
                      // So to avoid duplications we should first remove the value and then put it again.
                      updateValue(pair, colorUIResource, row, column);
                      changed.set(true);
                    }
                  }
                });
              } else if (value instanceof Boolean) {
                updateValue(pair, !((Boolean)value), row, column);
                changed.set(true);
              } else if (value instanceof Integer) {
                Integer newValue = editNumber(key.toString(), value.toString(), Integer::parseInt);
                if (newValue != null) {
                  updateValue(pair, newValue, row, column);
                  changed.set(true);
                }
              } else if (value instanceof Float) {
                Float newValue = editNumber(key.toString(), value.toString(), Float::parseFloat);
                if (newValue != null) {
                  updateValue(pair, newValue, row, column);
                  changed.set(true);
                }
              } else if (value instanceof EmptyBorder) {
                Insets i = ((Border)value).getBorderInsets(null);

                String oldInsets = String.format("%d,%d,%d,%d", i.top, i.left, i.bottom, i.right);
                Insets newInsets = editInsets(key.toString(), oldInsets);
                if (newInsets != null) {
                  updateValue(pair, new JBEmptyBorder(newInsets), row, column);
                  changed.set(true);
                }
              } else if (value instanceof Insets) {
                Insets i = (Insets)value;

                String oldInsets = String.format("%d,%d,%d,%d", i.top, i.left, i.bottom, i.right);
                Insets newInsets = editInsets(key.toString(), oldInsets);
                if (newInsets != null) {
                  updateValue(pair, newInsets, row, column);
                  changed.set(true);
                }
              } else if (value instanceof UIUtil.GrayFilter f) {
                String oldFilter = String.format("%d,%d,%d", f.getBrightness(), f.getContrast(), f.getAlpha());
                UIUtil.GrayFilter newValue = editGrayFilter(key.toString(), oldFilter);
                if (newValue != null) {
                  updateValue(pair, newValue, row, column);
                  changed.set(true);
                }
              } else if (value instanceof Font) {
                Font newValue = editFontSize(key.toString(), (Font)value);
                if (newValue != null) {
                  UIManager.getDefaults().remove(key);
                  UIManager.getDefaults().put(key, newValue);
                  setValueAt(newValue, row, column);
                  changed.set(true);
                }
              } else if (value instanceof Dimension d) {
                String oldDimension = String.format("%d,%d", d.width, d.height);
                Dimension newDimension = editDimension(key.toString(), oldDimension);
                if (newDimension != null) {
                  updateValue(pair, newDimension, row, column);
                  changed.set(true);
                }
              }

              if (changed.get()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                  LafManager.getInstance().repaintUI();
                });
              }
              PropertiesComponent.getInstance().setValue(LAST_SELECTED_KEY, key.toString());
            }
            return false;
          }

          void updateValue(Pair<?, ?> value, Object newValue, int row, int col) {
            UIManager.getDefaults().remove(value.first);
            UIManager.getDefaults().put(value.first, newValue);
            setValueAt(Pair.create(value.first, newValue), row, col);
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
            value = column == 0 ? ((Pair<?, ?>)value).first : ((Pair<?, ?>)value).second;
            if (value instanceof Boolean) {
              TableCellRenderer renderer = table.getDefaultRenderer(Boolean.class);
              if (renderer != null) {
                JCheckBox box = (JCheckBox)renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                box.setHorizontalAlignment(SwingConstants.LEFT);
                return box;
              }
            }
            final JLabel label = new JLabel(value == null ? "" : value.toString());
            final JPanel panel = simplePanel(label);
            if (value instanceof Color c) {
              label.setText(String.format("  [%d,%d,%d] #%s", c.getRed(), c.getGreen(), c.getBlue(), StringUtil.toUpperCase(ColorUtil.toHex(c))));
              Color fg = ColorUtil.isDark(c) ? Gray.xFF : Gray.x00;
              label.setForeground(fg);
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

        TableSpeedSearch.installOn(table, (o, cell) -> cell.column == 1 ? null : String.valueOf(o));
        table.setShowGrid(false);
        TableHoverListener.DEFAULT.removeFrom(table);
        myTable = table;
        mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            updateFilter();
          }
        });

        ScrollingUtil.installActions(myTable, true, mySearchField);

        myColorsOnly = new JBCheckBox(IdeBundle.message("checkbox.colors.only"), PropertiesComponent.getInstance().getBoolean(COLORS_ONLY_KEY, false)) {
          @Override
          public void addNotify() {
            super.addNotify();
            updateFilter();
            String key = PropertiesComponent.getInstance().getValue(LAST_SELECTED_KEY);
            if (key != null) {
              for (int i = 0; i < myTable.getRowCount(); i++) {
                Object valueAt = myTable.getModel().getValueAt(i, 0);
                if (valueAt instanceof Pair<?,?> && key.equals(((Pair<?, ?>)valueAt).first)) {
                  myTable.getSelectionModel().setLeadSelectionIndex(i);
                  myTable.getSelectionModel().setSelectionInterval(i, i);
                  ScrollingUtil.ensureIndexIsVisible(myTable, i, 1);
                  return;
                }
              }
              ScrollingUtil.ensureSelectionExists(myTable);
            }
          }
        };
        myColorsOnly.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            PropertiesComponent.getInstance().setValue(COLORS_ONLY_KEY, myColorsOnly.isSelected(), false);
            updateFilter();
          }
        });
        JPanel pane = ToolbarDecorator.createDecorator(myTable)
          .setToolbarPosition(ActionToolbarPosition.BOTTOM)
          .setAddAction((x) -> addNewValue())
          .createPanel();
        BorderLayoutPanel panel = simplePanel(simplePanel(pane).withBorder(JBUI.Borders.empty(5, 0)))
          .addToTop(top)
          .addToBottom(myColorsOnly);
        DataProvider provider = dataId -> {
          if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
            if ((mySearchField.hasFocus() && StringUtil.isEmpty(mySearchField.getSelectedText())) || myTable.hasFocus()) {
              int[] rows = myTable.getSelectedRows();
              if (rows.length > 0) {
                  return new TextCopyProvider() {
                    @Override
                    public @NotNull ActionUpdateThread getActionUpdateThread() {
                      return ActionUpdateThread.EDT;
                    }

                    @Override
                    public Collection<String> getTextLinesToCopy() {
                      List<String> result = new ArrayList<>();
                      String tail = rows.length > 1 ? "," : "";
                      for (int row : rows) {
                        var pair = (Pair<?, ?>)myTable.getModel().getValueAt(row, 0);
                        if (pair.second instanceof Color) {
                          result.add("\"" + pair.first.toString() + "\": \"" + ColorUtil.toHtmlColor((Color)pair.second) + "\"" + tail);
                        } else {
                          result.add("\"" + pair.first.toString() + "\": \"" + pair.second + "\"" + tail);
                        }
                      }

                      return result;
                    }
                  };
              }
            }
          }
          return null;
        };
        DataManager.registerDataProvider(myTable, provider);
        DataManager.registerDataProvider(mySearchField, provider);
        return panel;
      }

      private void addNewValue() {
        ApplicationManager.getApplication().invokeLater(() -> new DialogWrapper(myTable, true) {
          final JBTextField name = new JBTextField(40);
          final JBTextField value = new JBTextField(40);
          {
            setTitle(IdeBundle.message("dialog.title.add.new.value"));
            init();
          }

          @Override
          protected JComponent createCenterPanel() {
            return UI.PanelFactory.grid()
              .add(UI.PanelFactory.panel(name).withLabel(IdeBundle.message("label.ui.name")))
              .add(UI.PanelFactory.panel(value).withLabel(IdeBundle.message("label.ui.value")))
              .createPanel();
          }

          @Override
          public @NotNull JComponent getPreferredFocusedComponent() {
            return name;
          }

          @Override
          protected void doOKAction() {
            String key = name.getText().trim();
            String val = value.getText().trim();
            if (!key.isEmpty() && !val.isEmpty()) {
              UIManager.put(key, UITheme.parseValue(key, val));
              myTable.setModel(createFilteringModel());
              updateFilter();
            }
            super.doOKAction();
          }
        }.show());
      }

      private void updateFilter() {
        FilteringTableModel<?> model = (FilteringTableModel<?>)myTable.getModel();
        if (StringUtil.isEmpty(mySearchField.getText()) && !myColorsOnly.isSelected()) {
          model.setFilter(null);
          return;
        }

        MinusculeMatcher matcher = NameUtil.buildMatcher("*" + mySearchField.getText(), NameUtil.MatchingCaseSensitivity.NONE);
        model.setFilter(pair -> {
          Object obj = ((Pair<?, ?>)pair).second;
          String value;
          if (obj == null) {
            value = "null";
          } else if (obj instanceof Color) {
            value = ColorUtil.toHtmlColor((Color)obj);
          } else {
            value = obj.toString();
          }

          value = ((Pair<?, ?>)pair).first.toString() + " " + value;
          return (!myColorsOnly.isSelected() || obj instanceof Color) && matcher.matches(value);
        });

      }

      private @Nullable <T> T editNumber(String key, String value, Function<? super String, ? extends T> parser) {
        String newValue = Messages.showInputDialog(getRootPane(), IdeBundle.message("dialog.message.enter.new.value.for.0", key),
                                                   IdeBundle.message("dialog.title.number.editor"), null, value,
                                                   new InputValidator() {
                                     @Override
                                     public boolean checkInput(String inputString) {
                                       try {
                                         parser.apply(inputString);
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

        return newValue != null ? parser.apply(newValue) : null;
      }

      @Nullable
      private Insets editInsets(String key, String value) {
        String newValue = Messages.showInputDialog(getRootPane(),
                                                   IdeBundle.message("dialog.message.enter.new.value.for.0.in.form.top.left.bottom.right", key),
                                                   IdeBundle.message("dialog.title.insets.editor"), null, value,
                                                   new InputValidator() {
             @Override
             public boolean checkInput(String inputString) {
               return parseInsets(inputString) != null;
             }

             @Override
             public boolean canClose(String inputString) {
               return checkInput(inputString);
             }
           });

        return newValue != null ? parseInsets(newValue) : null;
      }

      @Nullable
      private static Insets parseInsets(String value) {
        String[] parts = value.split(",");
        if(parts.length != 4) {
          return null;
        }

        try {
          List<Integer> v = ContainerUtil.map(parts, p -> Integer.parseInt(p));
          return JBUI.insets(v.get(0), v.get(1), v.get(2), v.get(3));
        } catch (NumberFormatException nex) {
          return null;
        }
      }

      @Nullable
      private Dimension editDimension(String key, String value) {
        String newValue = Messages.showInputDialog(getRootPane(),
                                                   IdeBundle.message("dialog.message.enter.new.value.for.0.in.form.width.height", key),
                                                   IdeBundle.message("dialog.title.dimension.editor"), null, value,
                                                   new InputValidator() {
             @Override
             public boolean checkInput(String inputString) {
               return parseDimension(inputString) != null;
             }

             @Override
             public boolean canClose(String inputString) {
               return checkInput(inputString);
             }
           });

        return newValue != null ? parseDimension(newValue) : null;
      }

      @Nullable
      private static Dimension parseDimension(String value) {
        String[] parts = value.split(",");
        if(parts.length != 2) {
          return null;
        }

        try {
          List<Integer> v = ContainerUtil.map(parts, p -> Integer.parseInt(p));
          return JBUI.size(v.get(0), v.get(1));
        } catch (NumberFormatException nex) {
          return null;
        }
      }

      @Nullable
      private UIUtil.GrayFilter editGrayFilter(String key, String value) {
        String newValue = Messages.showInputDialog(getRootPane(),
                                                   IdeBundle.message(
                                                     "dialog.message.enter.new.value.for.0.in.form.brightness.contrast.alpha", key),
                                                   IdeBundle.message("dialog.title.gray.filter.editor"), null, value,
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
      private static UIUtil.GrayFilter parseGrayFilter(String value) {
        String[] parts = value.split(",");
        if(parts.length != 3) {
          return null;
        }

        try {
          List<Integer> v = ContainerUtil.map(parts, p -> Integer.parseInt(p));
          return new UIUtil.GrayFilter(v.get(0), v.get(1), v.get(2));
        } catch (NumberFormatException nex) {
          return null;
        }
      }

      @Nullable
      private Font editFontSize(String key, Font font) {
        String newValue = Messages.showInputDialog(getRootPane(),
                                                   IdeBundle.message("label.enter.new.font.size.for.0", key),
                                                   IdeBundle.message("dialog.title.font.size.editor"), null, Integer.toString(font.getSize()),
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
      private static Font parseFontSize(Font font, String value) {
        try {
          int newSize = Integer.parseInt(value);
          return (newSize > 0) ? font.deriveFont((float)newSize) : null;
        } catch (NumberFormatException nex) {
          return null;
        }
      }
    }.show();
  }


  private static Object[] @NotNull [] getUIDefaultsData() {
    final UIDefaults defaults = UIManager.getDefaults();
    Enumeration<?> keys = defaults.keys();
    final Object[][] data = new Object[defaults.size()][2];
    int i = 0;
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      Pair<Object, Object> row = Pair.create(key, defaults.get(key));
      data[i][0] = row;
      data[i][1] = row;
      i++;
    }

    Arrays.sort(data, (o1, o2) -> StringUtil.naturalCompare(((Pair<?, ?>)o1[0]).first.toString(), ((Pair<?, ?>)o2[0]).first.toString()));
    return data;
  }

  @NotNull
  private static FilteringTableModel<Object> createFilteringModel() {
    DefaultTableModel model = new DefaultTableModel(getUIDefaultsData(), new Object[]{"Name", "Value"}) {
      @Override
      public boolean isCellEditable(int row, int column) {
        if (column != 1) return false;
        Object value = ((Pair<?, ?>)getValueAt(row, column)).second;
        return (value instanceof Color ||
                value instanceof Boolean ||
                value instanceof Integer ||
                value instanceof Float ||
                value instanceof EmptyBorder ||
                value instanceof Insets ||
                value instanceof UIUtil.GrayFilter ||
                value instanceof Font ||
                value instanceof Dimension);
      }
    };
    FilteringTableModel<Object> filteringTableModel = new FilteringTableModel<>(model, Object.class);
    filteringTableModel.setFilter(null);
    return filteringTableModel;
  }


  private static class IconWrap implements Icon {
    private final Icon myIcon;

    IconWrap(Icon icon) {
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
