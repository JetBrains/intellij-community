// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxPopupState;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ComboBoxPopup<T> extends ListPopupImpl {
  private final Context<T> myContext;

  protected ComboBoxPopup(@NotNull Context<T> context, @Nullable Object selectedItem) {
    this(context, null, popupStateFromContext(context, selectedItem), null);
  }

  private ComboBoxPopup(@NotNull Context<T> context,
                        @Nullable WizardPopup aParent,
                        @NotNull MyBasePopupState<T> aStep,
                        @Nullable Object parentValue) {
    super(context.getProject(), aParent, aStep, parentValue);
    myContext = context;
    configurePopup();
  }

  @NotNull
  private static <T> MyBasePopupState<T> popupStateFromContext(@NotNull Context<T> context,
                                                               @Nullable Object selectedItem) {
    MyBasePopupState<T> step = new MyBasePopupState<T>(context, () -> context.getModel()) {
      @Override
      public void canceled() {
        context.onPopupStepCancelled();
      }
    };

    if (selectedItem != null) {
      //noinspection SuspiciousMethodCalls
      step.setDefaultOptionIndex(step.getValues().indexOf(selectedItem));
    }
    return step;
  }

  public interface Context<T> {
    @Nullable
    Project getProject();

    @NotNull
    ListModel<T> getModel();

    @NotNull
    ListCellRenderer<? super T> getRenderer();

    void setSelectedItem(T value);

    default int getMaximumRowCount() { return 10; }
    default void onPopupStepCancelled() {}
    default void configureList(@NotNull JList<T> list) {}
    default void customizeListRendererComponent(JComponent component) {}
  }

  public void syncWithModelChange() {
    //noinspection unchecked,rawtypes
    List<T> values = ((BaseListPopupStep)getStep()).getValues();
    values.clear();
    values.addAll(copyItemsFromModel(myContext.getModel()));
    JList<?> popupList = getList();
    updateVisibleRowCount();
    ((ListPopupModel<?>)popupList.getModel()).syncModel();
    popupList.setVisibleRowCount(Math.min(values.size(), myContext.getMaximumRowCount()));
    setSize(popupList.getPreferredSize());
  }

  @NotNull
  @Override
  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    if (step instanceof MyBasePopupState) {
      //noinspection unchecked
      return new ComboBoxPopup<>(myContext, parent, (MyBasePopupState<T>)step, parentValue);
    }

    throw new IllegalArgumentException(step.getClass().toString());
  }

  @Override
  public JList<T> getList() {
    //noinspection unchecked
    return super.getList();
  }

  @Override
  protected ListCellRenderer<T> getListElementRenderer() {
    // we set the correct renderer explicitly
    // to ensure getComponent().getPreferredSize() is computed
    // correctly. Fixes the sub-popup jump's to the left on macOS
    return new MyDelegateRenderer();
  }

  private void configurePopup() {
    setMaxRowCount(myContext.getMaximumRowCount());
    setRequestFocus(false);

    JList<T> list = getList();
    myContext.configureList(list);

    list.setSelectionForeground(UIManager.getColor("ComboBox.selectionForeground"));
    list.setSelectionBackground(UIManager.getColor("ComboBox.selectionBackground"));
    list.setBorder(null);
    list.setFocusable(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    Border border = UIManager.getBorder("ComboPopup.border");
    if (border != null) {
      getContent().setBorder(border);
    }
  }

  private class MyDelegateRenderer implements ListCellRenderer<T> {
    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      //noinspection unchecked
      Component component = myContext.getRenderer().getListCellRendererComponent(list, (T)value, index, isSelected, cellHasFocus);
      if (component instanceof JComponent && !(component instanceof JSeparator || component instanceof TitledSeparator)) {
        JComponent jComponent = (JComponent)component;
        jComponent.setBorder(JBUI.Borders.empty(2, 8));
        myContext.customizeListRendererComponent(jComponent);
      }
      return component;
    }
  }

  private static class MyBasePopupState<T> extends BaseListPopupStep<T> {
    private final JBList<T> myProxyList = new JBList<>();
    private final Context<T> myContext;
    private final Supplier<ListModel<T>> myGetComboboxModel;

    private MyBasePopupState(@NotNull Context<T> context,
                             @NotNull Supplier<ListModel<T>> getComboboxModel) {
      super("", copyItemsFromModel(getComboboxModel.get()));
      myGetComboboxModel = getComboboxModel;
      myContext = context;
    }

    @Nullable
    @Override
    @SuppressWarnings("rawtypes")
    public PopupStep onChosen(T selectedValue, boolean finalChoice) {
      ListModel<T> model = myGetComboboxModel.get();
      if (model instanceof ComboBoxPopupState) {
        //noinspection unchecked
        ListModel<T> nextModel = ((ComboBoxPopupState<T>)model).onChosen(selectedValue);
        if (nextModel != null) {
          return new MyBasePopupState<>(myContext, () -> nextModel);
        }
      }

      myContext.setSelectedItem(selectedValue);
      return FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(T selectedValue) {
      ListModel<T> model = myGetComboboxModel.get();
      if (model instanceof ComboBoxPopupState) {
        //noinspection unchecked
        return ((ComboBoxPopupState<T>)model).hasSubstep(selectedValue);
      }
      return super.hasSubstep(selectedValue);
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @NotNull
    @Override
    public String getTextFor(T value) {
      Component component = myContext.getRenderer().getListCellRendererComponent(myProxyList, value, -1, false, false);
      return component instanceof TitledSeparator || component instanceof JSeparator ? "" :
             component instanceof JLabel ? ((JLabel)component).getText() :
             component instanceof SimpleColoredComponent
             ? ((SimpleColoredComponent)component).getCharSequence(false).toString()
             : String.valueOf(value);
    }

    @Override
    public boolean isSelectable(T value) {
      Component component = myContext.getRenderer().getListCellRendererComponent(myProxyList, value, -1, false, false);
      return !(component instanceof TitledSeparator || component instanceof JSeparator);
    }
  }

  @NotNull
  private static <T> List<T> copyItemsFromModel(@NotNull ListModel<T> model) {
    ArrayList<T> items = new ArrayList<>(model.getSize());
    for (int i = 0, size = model.getSize(); i < size; i++) {
      items.add(model.getElementAt(i));
    }
    return items;
  }
}
