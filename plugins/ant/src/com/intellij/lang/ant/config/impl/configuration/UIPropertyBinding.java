// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.BaseTableView;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ListProperty;
import com.intellij.util.config.StorageProperty;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class UIPropertyBinding {
  public abstract void loadValues(AbstractProperty.AbstractPropertyContainer container);

  public abstract void apply(AbstractProperty.AbstractPropertyContainer container);

  public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
  }

  public abstract void beDisabled();

  public abstract void beEnabled();

  public abstract void addAllPropertiesTo(Collection<? super AbstractProperty> properties);

  public static final class Composite extends UIPropertyBinding {
    private final List<UIPropertyBinding> myBindings = new ArrayList<>();

    public void bindBoolean(JToggleButton toggleButton, AbstractProperty<Boolean> property) {
      ToggleButtonBinding binding = new ToggleButtonBinding(toggleButton, property);
      myBindings.add(binding);
    }

    public void bindInt(JTextComponent textComponent, AbstractProperty<Integer> property) {
      myBindings.add(new IntTextBinding(textComponent, property));
    }

    public TextBinding bindString(JTextComponent textComponent, AbstractProperty<@Nls String> property) {
      TextBinding textBinding = new TextBinding(textComponent, property);
      myBindings.add(textBinding);
      return textBinding;
    }

    @Override
    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      for (final UIPropertyBinding binding : myBindings) {
        binding.loadValues(container);
      }
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      for (final UIPropertyBinding propertyBinding : myBindings) {
        propertyBinding.apply(container);
      }
    }

    @Override
    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      for (final UIPropertyBinding binding : myBindings) {
        binding.beforeClose(container);
      }
    }

    public void bindString(JComboBox comboBox, AbstractProperty<@Nls String> property) {
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

    @Override
    public void beDisabled() {
      for (final UIPropertyBinding binding : myBindings) {
        binding.beDisabled();
      }
    }

    @Override
    public void beEnabled() {
      for (final UIPropertyBinding binding : myBindings) {
        binding.beEnabled();
      }
    }

    @Override
    public void addAllPropertiesTo(Collection<? super AbstractProperty> properties) {
      for (final UIPropertyBinding binding : myBindings) {
        binding.addAllPropertiesTo(properties);
      }
    }

    public <T> OrderListBinding<T> bindList(JList list, ListProperty<T> property) {
      OrderListBinding<T> binding = new OrderListBinding<>(list, property);
      addBinding(binding);
      return binding;
    }

    public void bindString(JLabel label, AbstractProperty<@Nls String> property) {
      addBinding(new ComponentBinding<>(label, property) {
        @Override
        public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
          getComponent().setText(getProperty().get(container));
        }

        @Override
        public void apply(AbstractProperty.AbstractPropertyContainer container) {
        }
      });
    }
  }

  @ApiStatus.Internal
  public abstract static class ComponentBinding<Comp extends JComponent, Prop extends AbstractProperty> extends UIPropertyBinding {
    private final Comp myComponent;
    private final Prop myProperty;

    protected ComponentBinding(Comp component, Prop property) {
      myComponent = component;
      myProperty = property;
    }

    @Override
    public void beDisabled() {
      myComponent.setEnabled(false);
    }

    @Override
    public void beEnabled() {
      myComponent.setEnabled(true);
    }

    @Override
    public void addAllPropertiesTo(Collection<? super AbstractProperty> properties) {
      properties.add(myProperty);
    }

    protected Comp getComponent() {
      return myComponent;
    }

    protected Prop getProperty() {
      return myProperty;
    }
  }

  static final class ToggleButtonBinding extends ComponentBinding<JToggleButton, AbstractProperty<Boolean>> {
    private final ChangeValueSupport myChangeSupport;

    ToggleButtonBinding(JToggleButton toggleButton, AbstractProperty<Boolean> property) {
      super(toggleButton, property);
      myChangeSupport = ChangeValueSupport.create(toggleButton, ListenerInstaller.TOGGLE_BUTTON, property.getName());
    }

    @Override
    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
      getComponent().setSelected(getProperty().get(container).booleanValue());
      myChangeSupport.start();
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      getProperty().set(container, getComponent().isSelected());
    }

    @Override
    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
    }
  }

  private abstract static class ListenerInstaller<Comp extends JComponent, Listener> {
    public static final ListenerInstaller<JToggleButton, ItemListener> TOGGLE_BUTTON =
      new ListenerInstaller<>() {
        @Override
        public ItemListener create(final PropertyChangeSupport changeSupport, final @NonNls String propertyName) {
          return new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
              changeSupport.firePropertyChange(propertyName, null, null);
            }
          };
        }

        @Override
        public void setListener(JToggleButton component, ItemListener listener) {
          component.getModel().addItemListener(listener);
        }

        @Override
        public void removeListener(JToggleButton component, ItemListener listener) {
          component.getModel().removeItemListener(listener);
        }
      };

    public abstract Listener create(PropertyChangeSupport changeSupport, @NonNls String propertyName);

    public abstract void setListener(Comp component, Listener documentListener);

    public abstract void removeListener(Comp component, Listener changeListener);

    public static final ListenerInstaller<JTextComponent, DocumentListener> TEXT_LISTENER_INSTALLER =
      new ListenerInstaller<>() {
        @Override
        public DocumentListener create(final PropertyChangeSupport changeSupport, final @NonNls String propertyName) {
          return new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
              changeSupport.firePropertyChange(propertyName, null, null);
            }
          };
        }

        @Override
        public void setListener(JTextComponent textComponent, DocumentListener documentListener) {
          textComponent.getDocument().addDocumentListener(documentListener);
        }

        @Override
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
    private final @NonNls String myPropertyName;

    ChangeValueSupport(Comp component, ListenerInstaller<Comp, Listener> installer, @NonNls String propertyName) {
      myComponent = component;
      myPropertyName = propertyName;
      myChangeSupport = new PropertyChangeSupport(myPropertyName);
      myInstaller = installer;
    }

    public static <Comp extends JComponent, Listener> ChangeValueSupport create(Comp component,
                                                                                ListenerInstaller<Comp, Listener> installer,
                                                                                @NonNls String propertyName) {
      return new ChangeValueSupport<>(component, installer, propertyName);
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

  public static class TextBinding extends ComponentBinding<JTextComponent, AbstractProperty<@Nls String>> {
    private final ChangeValueSupport myChangeSupport;

    public TextBinding(JTextComponent textComponent, AbstractProperty<@Nls String> property) {
      super(textComponent, property);
      myChangeSupport = ChangeValueSupport.create(textComponent, ListenerInstaller.TEXT_LISTENER_INSTALLER, property.getName());
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      getProperty().set(container, getComponent().getText());
    }

    @Override
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

    @Override
    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      myChangeSupport.stop();
    }
  }

  private static class IntTextBinding extends ComponentBinding<JTextComponent, AbstractProperty<Integer>> {
    IntTextBinding(JTextComponent textComponent, AbstractProperty<Integer> property) {
      super(textComponent, property);
    }

    @Override
    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      getComponent().setText(getProperty().get(container).toString());
    }

    @Override
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

  public static final class TableListBinding<T> extends ComponentBinding<JTable, ListProperty<T>> {
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
        @Override
        public void tableChanged(final TableModelEvent e) {
          final JTableHeader header = getComponent().getTableHeader();
          if (header != null) {
            header.repaint();
          }
        }
      });
    }

    @Override
    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      if (myColumnWidthProperty != null) {
        BaseTableView.restoreWidth(s -> myColumnWidthProperty.get(container).get(s), getComponent().getColumnModel());
      }
      myModel.setItems(myProperty.getModifiableList(container));
      if (myModel.isSortable()) {
        ColumnInfo[] columnInfos = myModel.getColumnInfos();
        for (ColumnInfo columnInfo : columnInfos) {
          if (columnInfo.isSortable()) {
            break;
          }
        }
      }
      TableUtil.ensureSelectionExists(getComponent());
    }

    @Override
    public void beDisabled() {
      for (JComponent component : myComponents) {
        component.setEnabled(false);
      }
    }

    @Override
    public void beEnabled() {
      for (JComponent component : myComponents) {
        component.setEnabled(true);
      }
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      myProperty.set(container, myModel.getItems());
    }

    @Override
    public void beforeClose(AbstractProperty.AbstractPropertyContainer container) {
      if (myColumnWidthProperty != null) {
        BaseTableView.storeWidth((s, s2) -> myColumnWidthProperty.get(container).put(s, s2), getComponent().getColumnModel());
      }
    }

    public void setColumnWidths(StorageProperty property) {
      myColumnWidthProperty = property;
      getComponent().getTableHeader().setResizingAllowed(true);
    }

    public void setSortable(boolean isSortable) {
      myModel.setSortable(isSortable);
    }
  }

  private abstract static class BaseListBinding<Item> extends UIPropertyBinding {
    private final List<JComponent> myComponents = new ArrayList<>();
    private final JList myList;
    private final ListProperty<Item> myProperty;

    protected BaseListBinding(ListProperty<Item> property, JList list) {
      myList = list;
      myProperty = property;
      myComponents.add(myList);
    }

    @Override
    public void beDisabled() {
      for (final JComponent component : myComponents) {
        component.setEnabled(false);
      }
    }

    @Override
    public void beEnabled() {
      for (final JComponent component : myComponents) {
        component.setEnabled(true);
      }
    }

    protected void addComponent(JComponent component) {
      myComponents.add(component);
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      ListModel model = myList.getModel();
      List<Item> list = new ArrayList<>();
      for (int i = 0; i < model.getSize(); i++) {
        list.add((Item)model.getElementAt(i));
      }
      myProperty.set(container, list);
    }

    @Override
    public void addAllPropertiesTo(Collection<? super AbstractProperty> properties) {
      properties.add(myProperty);
    }

    protected JList getList() {
      return myList;
    }

    protected ListProperty<Item> getProperty() {
      return myProperty;
    }
  }

  static final class OrderListBinding<T> extends BaseListBinding<T> {
    OrderListBinding(JList list, ListProperty<T> property) {
      super(property, list);
      list.setModel(new DefaultListModel());
    }

    @Override
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

    public void addAddManyFacility(JButton button, final Factory<? extends List<T>> factory) {
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          List<T> items = factory.create();
          getList().requestFocusInWindow();
          if (items == null || items.isEmpty()) {
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

  static final class ComboBoxBinding extends ComponentBinding<JComboBox, AbstractProperty<@Nls String>> {
    public ComboBoxBinding(JComboBox comboBox, AbstractProperty<@Nls String> property) {
      super(comboBox, property);
    }

    @Override
    public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      getComponent().getModel().setSelectedItem(getProperty().get(container));
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer container) {
      getProperty().set(container, (String)getComponent().getSelectedItem());
    }
  }
}
