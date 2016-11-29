/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.BaseTableView;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ListProperty;
import com.intellij.util.config.StorageProperty;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

public abstract class UIPropertyBinding {
  public abstract void loadValues(AbstractProperty.AbstractPropertyContainer container);

  public abstract void apply(AbstractProperty.AbstractPropertyContainer container);

  public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
  }

  public abstract void beDisabled();

  public abstract void beEnabled();

  public abstract void addAllPropertiesTo(Collection<AbstractProperty> properties);

  public static class Composite extends UIPropertyBinding {
    private final ArrayList<UIPropertyBinding> myBindings = new ArrayList<>();

    public ToggleButtonBinding bindBoolean(JToggleButton toggleButton, AbstractProperty<Boolean> property) {
      ToggleButtonBinding binding = new ToggleButtonBinding(toggleButton, property);
      myBindings.add(binding);
      return binding;
    }

    public void bindInt(JTextComponent textComponent, AbstractProperty<Integer> property) {
      myBindings.add(new IntTextBinding(textComponent, property));
    }

    public TextBinding bindString(JTextComponent textComponent, AbstractProperty<String> property) {
      TextBinding textBinding = new TextBinding(textComponent, property);
      myBindings.add(textBinding);
      return textBinding;
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      for (final UIPropertyBinding binding : myBindings) {
        binding.loadValues(container);
      }
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      for (final UIPropertyBinding propertyBinding : myBindings) {
        propertyBinding.apply(container);
      }
    }

    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      for (final UIPropertyBinding binding : myBindings) {
        binding.beforeClose(container);
      }
    }

    public void bindString(JComboBox comboBox, AbstractProperty<String> property) {
      myBindings.add(new ComboBoxBinding(comboBox, property));
    }

    public <T extends JDOMExternalizable> TableListBinding<T> bindList(JTable table, ColumnInfo[] columns, ListProperty<T> property) {
      final TableListBinding<T> binding = new TableListBinding<>(table, columns, property);
      myBindings.add(binding);
      return binding;
    }

    public void addBinding(UIPropertyBinding binding) {
      myBindings.add(binding);
    }

    public void beDisabled() {
      for (final UIPropertyBinding binding : myBindings) {
        binding.beDisabled();
      }
    }

    public void beEnabled() {
      for (final UIPropertyBinding binding : myBindings) {
        binding.beEnabled();
      }
    }

    public void addAllPropertiesTo(Collection<AbstractProperty> properties) {
      for (final UIPropertyBinding binding : myBindings) {
        binding.addAllPropertiesTo(properties);
      }
    }

    public <T> OrderListBinding<T> bindList(JList list, ListProperty<T> property) {
      OrderListBinding<T> binding = new OrderListBinding<>(list, property);
      addBinding(binding);
      return binding;
    }

    public void bindString(JLabel label, AbstractProperty<String> property) {
      addBinding(new ComponentBinding<JLabel, AbstractProperty<String>>(label, property) {
        public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
          getComponent().setText(getProperty().get(container));
        }

        public void apply(AbstractProperty.AbstractPropertyContainer container) {
        }
      });
    }
  }

  private static abstract class ComponentBinding<Comp extends JComponent, Prop extends AbstractProperty> extends UIPropertyBinding {
    private final Comp myComponent;
    private final Prop myProperty;

    protected ComponentBinding(Comp component, Prop property) {
      myComponent = component;
      myProperty = property;
    }

    public void beDisabled() {
      myComponent.setEnabled(false);
    }

    public void beEnabled() {
      myComponent.setEnabled(true);
    }

    public void addAllPropertiesTo(Collection<AbstractProperty> properties) {
      properties.add(myProperty);
    }

    protected Comp getComponent() {
      return myComponent;
    }

    protected Prop getProperty() {
      return myProperty;
    }
  }

  public static class ToggleButtonBinding extends ComponentBinding<JToggleButton, AbstractProperty<Boolean>> {
    private final ChangeValueSupport myChangeSupport;

    public ToggleButtonBinding(JToggleButton toggleButton, AbstractProperty<Boolean> property) {
      super(toggleButton, property);
      myChangeSupport = ChangeValueSupport.create(toggleButton, ListenerInstaller.TOGGLE_BUTTON, property.getName());
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
      getComponent().setSelected(getProperty().get(container).booleanValue());
      myChangeSupport.start();
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      getProperty().set(container, getComponent().isSelected());
    }

    public void addChangeListener(PropertyChangeListener listener) {
      myChangeSupport.addListener(listener);
    }

    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
    }
  }

  private static abstract class ListenerInstaller<Comp extends JComponent, Listener> {
    public static final ListenerInstaller<JToggleButton, ItemListener> TOGGLE_BUTTON =
      new ListenerInstaller<JToggleButton, ItemListener>() {
        public ItemListener create(final PropertyChangeSupport changeSupport, final String propertyName) {
          return new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
              changeSupport.firePropertyChange(propertyName, null, null);
            }
          };
        }

        public void setListener(JToggleButton component, ItemListener listener) {
          component.getModel().addItemListener(listener);
        }

        public void removeListener(JToggleButton component, ItemListener listener) {
          component.getModel().removeItemListener(listener);
        }
      };

    public abstract Listener create(PropertyChangeSupport changeSupport, String propertyName);

    public abstract void setListener(Comp component, Listener documentListener);

    public abstract void removeListener(Comp component, Listener changeListener);

    public final static ListenerInstaller<JTextComponent, DocumentListener> TEXT_LISTENER_INSTALLER =
      new ListenerInstaller<JTextComponent, DocumentListener>() {
        public DocumentListener create(final PropertyChangeSupport changeSupport, final String propertyName) {
          return new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
              changeSupport.firePropertyChange(propertyName, null, null);
            }
          };
        }

        public void setListener(JTextComponent textComponent, DocumentListener documentListener) {
          textComponent.getDocument().addDocumentListener(documentListener);
        }

        public void removeListener(JTextComponent textComponent, DocumentListener documentListener) {
          textComponent.getDocument().removeDocumentListener(documentListener);
        }
      };
  }

  private static class ChangeValueSupport<Comp extends JComponent, Listener> {
    private final Comp myComponent;
    private final ListenerInstaller<Comp, Listener> myInstaller;
    private final PropertyChangeSupport myChangeSupport;
    private Listener myChangeListener = null;
    private final String myPropertyName;

    public ChangeValueSupport(Comp component, ListenerInstaller<Comp, Listener> installer, String propertyName) {
      myComponent = component;
      myPropertyName = propertyName;
      myChangeSupport = new PropertyChangeSupport(myPropertyName);
      myInstaller = installer;
    }

    public static <Comp extends JComponent, Listener> ChangeValueSupport create(Comp component,
                                                                                ListenerInstaller<Comp, Listener> installer,
                                                                                String propertyName) {
      return new ChangeValueSupport(component, installer, propertyName);
    }

    public void stop() {
      if (myChangeListener != null) {
        myInstaller.removeListener(myComponent, myChangeListener);
      }
    }

    public void start() {
      if (myChangeListener != null) {
        myInstaller.setListener(myComponent, myChangeListener);
      }

    }

    public void addListener(PropertyChangeListener listener) {
      myChangeSupport.addPropertyChangeListener(listener);
      if (myChangeListener != null) {
        return;
      }
      myChangeListener = myInstaller.create(myChangeSupport, myPropertyName);
      myInstaller.setListener(myComponent, myChangeListener);
    }
  }

  public static class TextBinding extends ComponentBinding<JTextComponent, AbstractProperty<String>> {
    private final ChangeValueSupport myChangeSupport;

    public TextBinding(JTextComponent textComponent, AbstractProperty<String> property) {
      super(textComponent, property);
      myChangeSupport = ChangeValueSupport.create(textComponent, ListenerInstaller.TEXT_LISTENER_INSTALLER, property.getName());
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      getProperty().set(container, getComponent().getText());
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
      loadValuesImpl(container);
      myChangeSupport.start();
    }

    protected void loadValuesImpl(AbstractProperty.AbstractPropertyContainer container) {
      getComponent().setText(getProperty().get(container));
    }

    public void addChangeListener(PropertyChangeListener listener) {
      myChangeSupport.addListener(listener);
    }

    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
    }
  }

  private static class IntTextBinding extends ComponentBinding<JTextComponent, AbstractProperty<Integer>> {
    public IntTextBinding(JTextComponent textComponent, AbstractProperty<Integer> property) {
      super(textComponent, property);
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      getComponent().setText(getProperty().get(container).toString());
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      int value;
      try {
        value = Integer.parseInt(getComponent().getText());
      }
      catch (NumberFormatException e) {
        return;
      } // TODO[dyoma] report error
      getProperty().set(container, value);
    }
  }

  public static class TableListBinding<T> extends ComponentBinding<JTable, ListProperty<T>> {
    private final ListProperty<T> myProperty;
    private final ListTableModel<T> myModel;
    private final Collection<JComponent> myComponents = new ArrayList<>();
    private StorageProperty myColumnWidthProperty;

    public TableListBinding(final JTable table, ColumnInfo[] columns, ListProperty<T> property) {
      super(table, property);
      myProperty = property;
      myComponents.add(table);
      final JTableHeader header = table.getTableHeader();
      header.setReorderingAllowed(false);
      header.setResizingAllowed(false);
      TableColumnModel columnModel = table.getColumnModel();
      myModel = new ListTableModel<>(columns);
      myModel.setSortable(false);
      table.setModel(myModel);
      for (int i = 0; i < columns.length; i++) {
        ColumnInfo column = columns[i];
        TableColumn tableColumn = columnModel.getColumn(i);
        TableCellEditor editor = column.getEditor(null);
        if (editor != null) {
          tableColumn.setCellEditor(editor);
        }
        int wight = column.getWidth(table);
        if (wight > 0) {
          tableColumn.setMinWidth(wight);
          tableColumn.setMaxWidth(wight);
        }
      }
      table.setSurrendersFocusOnKeystroke(true);
      // support for sorting
      myModel.addTableModelListener(new TableModelListener() {
        public void tableChanged(final TableModelEvent e) {
          final JTableHeader header = getComponent().getTableHeader();
          if (header != null) {
            header.repaint();
          }
        }
      });
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      if (myColumnWidthProperty != null) {
        BaseTableView.restoreWidth(myColumnWidthProperty.get(container), getComponent().getColumnModel());
      }
      myModel.setItems(myProperty.getModifiableList(container));
      if (myModel.isSortable()) {
        final ColumnInfo[] columnInfos = myModel.getColumnInfos();
        int sortByColumn = -1;
        for (int idx = 0; idx < columnInfos.length; idx++) {
          ColumnInfo columnInfo = columnInfos[idx];
          if (columnInfo.isSortable()) {
            sortByColumn = idx;
            break;
          }
        }
      }
      TableUtil.ensureSelectionExists(getComponent());
    }

    public void beDisabled() {
      for (JComponent component : myComponents) {
        component.setEnabled(false);
      }
    }

    public void beEnabled() {
      for (JComponent component : myComponents) {
        component.setEnabled(true);
      }
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      myProperty.set(container, myModel.getItems());
    }

    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      if (myColumnWidthProperty != null) {
        BaseTableView.storeWidth(myColumnWidthProperty.get(container), getComponent().getColumnModel());
      }
    }

    public void setColumnWidths(StorageProperty property) {
      myColumnWidthProperty = property;
      getComponent().getTableHeader().setResizingAllowed(true);
    }

    public void addAddFacility(JButton addButton, final Factory<T> factory) {
      myComponents.add(addButton);
      addButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          JTable table = getComponent();
          if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
            return;
          }
          T item = factory.create();
          if (item == null) {
            return;
          }
          ArrayList<T> items = new ArrayList<>(myModel.getItems());
          items.add(item);
          myModel.setItems(items);
          int newIndex = myModel.indexOf(item);
          ListSelectionModel selectionModel = table.getSelectionModel();
          selectionModel.clearSelection();
          selectionModel.setSelectionInterval(newIndex, newIndex);
          ColumnInfo[] columns = myModel.getColumnInfos();
          for (int i = 0; i < columns.length; i++) {
            ColumnInfo column = columns[i];
            if (column.isCellEditable(item)) {
              table.requestFocusInWindow();
              table.editCellAt(newIndex, i);
              break;
            }
          }
        }
      });
    }

    public void setSortable(boolean isSortable) {
      myModel.setSortable(isSortable);
    }

    public void addRemoveFacility(final JButton button, final Condition<T> removable) {
      myComponents.add(button);
      button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          TableUtil.removeSelectedItems(getComponent());
        }
      });
      getComponent().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateRemoveButton(button, removable);
        }
      });
    }

    public void updateRemoveButton(JButton button, Condition<T> removable) {
      final JTable table = getComponent();
      final ListSelectionModel selectionModel = table.getSelectionModel();
      boolean enable = false;
      if (!selectionModel.isSelectionEmpty()) {
        enable = true;
        for (int i : table.getSelectedRows()) {
          if (!removable.value(myModel.getItems().get(i))) {
            enable = false;
            break;
          }
        }
      }
      button.setEnabled(enable);
    }
  }

  private static abstract class BaseListBinding<Item> extends UIPropertyBinding {
    private final ArrayList<JComponent> myComponents = new ArrayList<>();
    private final JList myList;
    private final ListProperty<Item> myProperty;

    protected BaseListBinding(ListProperty<Item> property, JList list) {
      myList = list;
      myProperty = property;
      myComponents.add(myList);
    }

    public void beDisabled() {
      for (final JComponent component : myComponents) {
        component.setEnabled(false);
      }
    }

    public void beEnabled() {
      for (final JComponent component : myComponents) {
        component.setEnabled(true);
      }
    }

    protected void addComponent(JComponent component) {
      myComponents.add(component);
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      ListModel model = myList.getModel();
      ArrayList<Item> list = new ArrayList<>();
      for (int i = 0; i < model.getSize(); i++) {
        list.add((Item)model.getElementAt(i));
      }
      myProperty.set(container, list);
    }

    public void addAllPropertiesTo(Collection<AbstractProperty> properties) {
      properties.add(myProperty);
    }

    protected JList getList() {
      return myList;
    }

    protected ListProperty<Item> getProperty() {
      return myProperty;
    }
  }

  public static class SortedListBinding<T extends JDOMExternalizable> extends BaseListBinding<T> {
    public SortedListBinding(JList list, ListProperty<T> property, Comparator<T> comparator) {
      super(property, list);
      list.setModel(new SortedListModel<>(comparator));
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      getModel().setAll(getProperty().get(container));
      ScrollingUtil.ensureSelectionExists(getList());
    }

    private SortedListModel<T> getModel() {
      return ((SortedListModel<T>)getList().getModel());
    }
  }

  public static class OrderListBinding<T> extends BaseListBinding<T> {
    public OrderListBinding(JList list, ListProperty<T> property) {
      super(property, list);
      list.setModel(new DefaultListModel());
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      DefaultListModel model = getModel();
      model.clear();
      Iterator iterator = getProperty().getIterator(container);
      while (iterator.hasNext()) {
        Object item = iterator.next();
        model.addElement(item);
      }
      ScrollingUtil.ensureSelectionExists(getList());
    }

    private DefaultListModel getModel() {
      return ((DefaultListModel)getList().getModel());
    }

    public void addAddManyFacility(JButton button, final Factory<List<T>> factory) {
      button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          List<T> items = factory.create();
          getList().requestFocusInWindow();
          if (items == null || items.size() == 0) {
            return;
          }
          for (final T item : items) {
            getModel().addElement(item);
            ScrollingUtil.selectItem(getList(), item);
          }
        }
      });
      addComponent(button);
    }
  }

  public static class ComboBoxBinding extends ComponentBinding<JComboBox, AbstractProperty<String>> {
    public ComboBoxBinding(JComboBox comboBox, AbstractProperty<String> property) {
      super(comboBox, property);
    }

    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      getComponent().getModel().setSelectedItem(getProperty().get(container));
    }

    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      getProperty().set(container, (String)getComponent().getSelectedItem());
    }
  }
}
