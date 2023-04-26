// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.internal.inspector.ComponentPropertiesCollector;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorAction;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.StripeTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.picker.ColorListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT;
import static com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT;

final class InspectorTable extends JBSplitter implements DataProvider, Disposable {
  private final @Nullable Project myProject;
  private final MyModel myModel;
  private StripeTable myTable;
  private ConsoleView myPreviewComponent;

  InspectorTable(@NotNull final List<? extends PropertyBean> clickInfo, @Nullable Project project) {
    super(true, 0.75f);
    myProject = project;
    myModel = new MyModel(clickInfo);
    init(null);
  }

  InspectorTable(@NotNull final Component component, @Nullable Project project) {
    super(true, 0.75f);
    myProject = project;
    myModel = new MyModel(component);
    init(component);
  }

  private void init(@Nullable Component component) {
    setSplitterProportionKey("UiInspector.table.splitter.proportion");

    myTable = new StripeTable(myModel);
    TableSpeedSearch.installOn(myTable);

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn propertyColumn = columnModel.getColumn(0);
    propertyColumn.setMinWidth(JBUIScale.scale(220));
    propertyColumn.setMaxWidth(JBUIScale.scale(220));
    propertyColumn.setResizable(false);
    propertyColumn.setCellRenderer(new PropertyNameRenderer());

    TableColumn valueColumn = columnModel.getColumn(1);
    valueColumn.setMinWidth(JBUIScale.scale(200));
    valueColumn.setResizable(false);
    valueColumn.setCellRenderer(new ValueCellRenderer());
    valueColumn.setCellEditor(new DefaultCellEditor(new JBTextField()) {
      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component comp = table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, false, false, row, column);
        Object realValue = table.getModel().getValueAt(row, column);
        if (comp instanceof JLabel) {
          value = ((JLabel)comp).getText();
        }
        if (realValue instanceof Color) {
          Rectangle cellRect = table.getCellRect(row, column, true);
          ColorChooserService.getInstance().showPopup(null, (Color)realValue, new ColorListener() {
            @Override
            public void colorChanged(Color color, Object source) {
              if (component != null) {
                component.setBackground(color);
                String name = myModel.myProperties.get(row).propertyName;
                myModel.myProperties.set(row, new PropertyBean(name, color));
              }
            }
          }, new RelativePoint(table, new Point(cellRect.x + JBUI.scale(6), cellRect.y + cellRect.height)));
          return null;
        }
        Component result = super.getTableCellEditorComponent(table, value, isSelected, row, column);
        ((JComponent)result).setBorder(BorderFactory.createLineBorder(JBColor.GRAY, 1));
        return result;
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        int row = myTable.rowAtPoint(event.getPoint());
        int column = 1;
        if (row >= 0 && row < myTable.getRowCount() && column < myTable.getColumnCount()) {
          Component renderer = myTable.getCellRenderer(row, column)
            .getTableCellRendererComponent(myTable, myModel.getValueAt(row, column), false, false, row, column);
          if (renderer instanceof JLabel) {
            StringBuilder sb = new StringBuilder();
            if (component != null) sb.append(UiInspectorUtil.getComponentName(component)).append(" ");
            String value = StringUtil.trimStart(((JLabel)renderer).getText().replace("\r", "").replace("\tat", "\n\tat"), "at ");
            sb.append("'").append(myModel.getValueAt(row, 0)).append("':");
            sb.append(value.contains("\n") || value.length() > 100 ? "\n" : " ");
            sb.append(value);
            //noinspection UseOfSystemOutOrSystemErr
            System.out.println(sb);
            return true;
          }
        }
        return false;
      }
    }.installOn(myTable);

    MyCellSelectionListener selectionListener = new MyCellSelectionListener();
    myTable.getSelectionModel().addListSelectionListener(selectionListener);
    myTable.getColumnModel().getSelectionModel().addListSelectionListener(selectionListener);

    myTable.setCellSelectionEnabled(true);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

    setFirstComponent(new JBScrollPane(myTable));
    if (component != null) {
      setSecondComponent(new DimensionsComponent(component));
    }
  }

  public void refresh() {
    myModel.refresh();
    if (myModel.myComponent != null) {
      setSecondComponent(new DimensionsComponent(myModel.myComponent));
    }
  }

  @Override
  public void dispose() {
    if (myPreviewComponent != null) {
      Disposer.dispose(myPreviewComponent);
    }
  }

  @NotNull
  public String getCellTextValue(int row, int col) {
    Object value = myTable.getValueAt(row, col);
    if (value instanceof String) return (String)value;

    TableColumn tableColumn = myTable.getColumnModel().getColumn(col);
    Component component = tableColumn.getCellRenderer().getTableCellRendererComponent(myTable, value, false, false, row, col);
    if (component instanceof JLabel) { // see ValueCellRenderer
      return ((JLabel)component).getText();
    }
    return String.valueOf(value);
  }


  public JTable getTable() {
    return myTable;
  }

  public TableModel getModel() {
    return myModel;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return new MyInspectorTableCopyProvider();
    }
    return null;
  }

  private static class MyModel extends AbstractTableModel {
    final Component myComponent;
    final List<PropertyBean> myProperties = new ArrayList<>();

    MyModel(@NotNull List<? extends PropertyBean> clickInfo) {
      myComponent = null;
      myProperties.addAll(clickInfo);
    }

    MyModel(@NotNull Component c) {
      myComponent = c;
      myProperties.addAll(ComponentPropertiesCollector.collect(c));
    }

    @Override
    @Nullable
    public Object getValueAt(int row, int column) {
      final PropertyBean bean = myProperties.get(row);
      if (bean != null) {
        return column == 0 ? bean.propertyName : bean.propertyValue;
      }

      return null;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return col == 1 && updater(myProperties.get(row)) != null;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      PropertyBean bean = myProperties.get(row);
      try {
        myProperties.set(row, new PropertyBean(bean.propertyName, Objects.requireNonNull(updater(bean)).fun(value)));
      }
      catch (Exception ignored) {
      }
    }

    @Nullable
    public Function<Object, Object> updater(PropertyBean bean) {
      if (myComponent == null) return null;

      String name = bean.propertyName.trim();
      try {
        try {
          Method getter;
          try {
            getter = myComponent.getClass().getMethod("get" + StringUtil.capitalize(name));
          }
          catch (Exception e) {
            getter = myComponent.getClass().getMethod("is" + StringUtil.capitalize(name));
          }
          final Method finalGetter = getter;
          final Method setter = myComponent.getClass().getMethod("set" + StringUtil.capitalize(name), getter.getReturnType());
          setter.setAccessible(true);
          return o -> {
            try {
              setter.invoke(myComponent, fromObject(o, finalGetter.getReturnType()));
              return finalGetter.invoke(myComponent);
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          };
        }
        catch (Exception e) {
          final Field field = ReflectionUtil.findField(myComponent.getClass(), null, name);
          if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
            return null;
          }
          return o -> {
            try {
              field.set(myComponent, fromObject(o, field.getType()));
              return field.get(myComponent);
            }
            catch (Exception e1) {
              throw new RuntimeException(e1);
            }
          };
        }
      }
      catch (Exception ignored) {
      }
      return null;
    }

    /**
     * @noinspection UseJBColor
     */
    private static Object fromObject(Object o, Class<?> type) {
      if (o == null) return null;
      if (type.isAssignableFrom(o.getClass())) return o;
      if ("null".equals(o)) return null;

      String value = String.valueOf(o).trim();
      if (type == int.class) return Integer.parseInt(value);
      if (type == boolean.class) return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
      if (type == byte.class) return Byte.parseByte(value);
      if (type == short.class) return Short.parseShort(value);
      if (type == double.class) return Double.parseDouble(value);
      if (type == float.class) return Float.parseFloat(value);

      String[] s = value.split("(?i)\\s*(?:[x@:]|[a-z]+:)\\s*", 6);
      if (type == Dimension.class) {
        if (s.length == 2) return new Dimension(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
      }
      else if (type == Point.class) {
        if (s.length == 2) return new Point(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
      }
      else if (type == Rectangle.class) {
        if (s.length >= 5) {
          return new Rectangle(Integer.parseInt(s[3]), Integer.parseInt(s[4]),
                               Integer.parseInt(s[1]), Integer.parseInt(s[2]));
        }
      }
      else if (type == Insets.class) {
        if (s.length >= 5) {
          //noinspection UseDPIAwareInsets
          return new Insets(Integer.parseInt(s[1]), Integer.parseInt(s[2]),
                            Integer.parseInt(s[4]), Integer.parseInt(s[4]));
        }
      }
      else if (type == Color.class) {
        if (s.length >= 5) {
          return new ColorUIResource(
            new Color(Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]), Integer.parseInt(s[4])));
        }
      }
      else if (type.getSimpleName().contains("ArrayTable")) {
        return "ArrayTable!";
      }
      throw new UnsupportedOperationException(type.toString());
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public int getRowCount() {
      return myProperties.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnIndex == 0 ? "Property" : "Value";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0 ? String.class : Object.class;
    }

    public void refresh() {
      myProperties.clear();
      myProperties.addAll(ComponentPropertiesCollector.collect(myComponent));
      fireTableDataChanged();
    }
  }

  private class MyCellSelectionListener implements ListSelectionListener {
    private String selectedProperty = null;

    @Override
    public void valueChanged(ListSelectionEvent e) {
      int row = myTable.getSelectedRow();
      int column = myTable.getSelectedColumn();
      if (row < 0 || column != 1) {
        return;
      }
      String property = myTable.getValueAt(row, 0).toString();
      @Nullable Object value = myTable.getValueAt(row, 1);
      if (property.equals(selectedProperty)) {
        return;
      }
      selectedProperty = property;

      if (value instanceof Dimension || value instanceof Rectangle || value instanceof Border || value instanceof Insets) {
        if (myModel.myComponent != null) {
          setSecondComponent(new DimensionsComponent(myModel.myComponent));
        }
      }
      else if (myProject != null) {
        if (myPreviewComponent == null) {
          myPreviewComponent = TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
          JComponent consoleComponent = myPreviewComponent.getComponent();
          consoleComponent.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 1));
          setSecondComponent(consoleComponent);
        }

        String strValue = String.valueOf(value);
        myPreviewComponent.clear();
        if (property.equals("added-at")) {
          myPreviewComponent.print(strValue, ERROR_OUTPUT);
        }
        else if (property.equals("text")) {
          myPreviewComponent.print(strValue, NORMAL_OUTPUT);
        }
        else if (property.trim().equals("hierarchy")) {
          String[] classNames = strValue.split(" *→ *");
          printClassNamesToConsole(myPreviewComponent, classNames, true);
        }
        else if (property.equals("toString")) {
          printToStringProperty(myPreviewComponent, strValue);
        }
        else if (property.contains("Listeners") && value != null) {
          String listeners = ValueCellRenderer.getToStringValue(value);
          String[] classNames = listeners.split(" *, *");
          printClassNamesToConsole(myPreviewComponent, classNames, false);
        }
        else if (property.equals("clientProperties") && value != null) {
          Map<Object, Object> properties = ValueCellRenderer.parseClientProperties(value);
          if (properties != null) {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
              if (entry.getKey().equals(UiInspectorAction.ADDED_AT_STACKTRACE)) continue;
              myPreviewComponent.print(entry.getKey() + " -> ", NORMAL_OUTPUT);
              printClassName(myPreviewComponent, String.valueOf(entry.getValue()));
              myPreviewComponent.print("\n", NORMAL_OUTPUT);
            }
          }
        }
        else if (value instanceof CachedImageIcon) {
          myPreviewComponent.print("Icon path: ", NORMAL_OUTPUT);
          printIconPath(myPreviewComponent, (CachedImageIcon)value);
        }
        else {
          String renderedValue = getCellTextValue(row, column);
          printClassName(myPreviewComponent, renderedValue);
        }

        myPreviewComponent.scrollTo(0);
        setSecondComponent(myPreviewComponent.getComponent());
      }
    }

    private void printClassNamesToConsole(ConsoleView consoleView, String[] classNames, boolean withIndent) {
      for (int idx = 0; idx < classNames.length; idx++) {
        if (withIndent) {
          consoleView.print("\t".repeat(idx), NORMAL_OUTPUT);
        }
        String className = classNames[idx];
        printClassName(consoleView, className);
        consoleView.print("\n", NORMAL_OUTPUT);
      }
    }

    private void printClassName(ConsoleView consoleView, String className) {
      String[] parts = className.split("@");  // there can be a class name with hashcode
      String classFqn = parts[0];
      PsiElement classElement = UiInspectorUtil.findClassByFqn(myProject, classFqn);
      if (classElement != null) {
        consoleView.printHyperlink(classFqn, project -> UiInspectorUtil.openClassByFqn(project, classFqn, true));
        if (parts.length > 1) {
          consoleView.print("@" + parts[1], NORMAL_OUTPUT);
        }
      }
      else {
        consoleView.print(className, NORMAL_OUTPUT);
      }
    }

    private void printToStringProperty(ConsoleView consoleView, Object value) {
      String strValue = value.toString();
      int classNameEnd = strValue.indexOf("[");
      if (classNameEnd == -1) return;
      String className = strValue.substring(0, classNameEnd);
      String content = strValue.substring(classNameEnd + 1, strValue.length() - 1);
      String[] properties = content.split(" *, *");

      printClassName(consoleView, className);
      consoleView.print(" " + strValue.charAt(classNameEnd) + "\n", NORMAL_OUTPUT);
      for (String prop : properties) {
        if (prop.isEmpty()) continue;
        consoleView.print("\t", NORMAL_OUTPUT);
        String[] keyValuePair = prop.split("=");
        if (keyValuePair.length == 1) {
          consoleView.print(keyValuePair[0], NORMAL_OUTPUT);
          if (prop.contains("=")) {
            consoleView.print("=", NORMAL_OUTPUT);
          }
        }
        else {
          String key = keyValuePair[0];
          consoleView.print(key + "=", NORMAL_OUTPUT);
          printClassName(consoleView, keyValuePair[1]);
        }
        consoleView.print("\n", NORMAL_OUTPUT);
      }
      consoleView.print("]", NORMAL_OUTPUT);
    }

    private void printIconPath(ConsoleView consoleView, CachedImageIcon icon) {
      URL iconUrl = icon.getUrl();
      if (iconUrl != null) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(iconUrl.getPath());
        if (file != null && myProject != null) {
          Navigatable navigatable = PsiNavigationSupport.getInstance().createNavigatable(myProject, file, 0);
          consoleView.printHyperlink(file.getPath(), project -> navigatable.navigate(true));
        }
        else {
          consoleView.print(iconUrl.toString(), NORMAL_OUTPUT);
        }
      }
      else {
        consoleView.print(String.valueOf(icon.getOriginalPath()), NORMAL_OUTPUT);
      }
    }
  }

  private static class PropertyNameRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final TableModel model = table.getModel();
      boolean changed = false;
      if (model instanceof MyModel) {
        changed = ((MyModel)model).myProperties.get(row).changed;
      }

      Color fg = isSelected ? table.getSelectionForeground()
                            : changed ? JBUI.CurrentTheme.Link.Foreground.ENABLED
                                      : table.getForeground();
      final JBFont font = JBFont.label();
      setFont(changed ? font.asBold() : font);
      setForeground(fg);
      setBorder(new JBEmptyBorder(2, 3, 2, 3));
      return this;
    }
  }

  private class MyInspectorTableCopyProvider implements CopyProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      int[] rows = myTable.getSelectedRows();
      int[] columns = myTable.getSelectedColumns();

      StringBuilder builder = new StringBuilder();
      for (int row : rows) {
        if (!builder.isEmpty()) builder.append('\n');

        for (int col : columns) {
          builder.append(getCellTextValue(row, col));
          if (col < myTable.getColumnCount() - 1) builder.append("\t");
        }
      }

      CopyPasteManager.getInstance().setContents(new TextTransferable(builder.toString()));
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return myTable.getSelectedRowCount() > 0;
    }
  }
}
