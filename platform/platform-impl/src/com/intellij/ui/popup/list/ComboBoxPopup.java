// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxPopupState;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.util.GroupedValue;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ComboBoxPopup<T> extends ListPopupImpl {
  private final Context<T> myContext;

  public ComboBoxPopup(@NotNull Context<T> context,
                       @Nullable T selectedItem,
                       @NotNull Consumer<? super T> onItemSelected) {
    this(context, null, popupStateFromContext(context, onItemSelected, selectedItem), null);
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
                                                               @NotNull Consumer<? super T> onItemSelected,
                                                               @Nullable T selectedItem) {
    MyBasePopupState<T> step = new MyBasePopupState<>(onItemSelected,
                                                      () -> context.getModel(),
                                                      () -> context.getRenderer()) {
      @Override
      public void canceled() {
        context.onPopupStepCancelled();
      }
    };

    if (selectedItem != null) {
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

    default int getMaximumRowCount() { return 10; }
    default void onPopupStepCancelled() {}
    default void configureList(@NotNull JList<T> list) {}
    default void customizeListRendererComponent(JComponent component) {}
  }

  public interface SelectionListener<T> extends EventListener {
    void setSelectedItem(@NotNull T value);
  }

  public void syncWithModelChange() {
    //mouse may be under the expandable item, sub-popup would be shown,
    //this popup could grow/shrink making the sub-popup mispositioned,
    //while probability is not high, we just hide child popups
    disposeChildren();

    //noinspection unchecked,rawtypes
    MyBasePopupState<T> step = (MyBasePopupState)getStep();
    List<T> values = step.getValues();
    values.clear();
    values.addAll(copyItemsFromModel(myContext.getModel()));
    JList<?> popupList = getList();
    ((ListPopupModel<?>)popupList.getModel()).syncModel();

    //AbstractPopup#show sets preferred size, we need to turn if off shortly
    JComponent content = getContent();
    content.setPreferredSize(null);

    Dimension newSize = content.getPreferredSize();
    content.setPreferredSize(newSize);
    setSize(newSize);

    //this method makes the popup drift towards SW because of the myContent#insets
    moveToFitScreen();
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
    // correctly. Fixes the sub-popup jump's to the left on mac
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
    private final Consumer<? super T> myOnItemSelected;
    private final @NotNull Supplier<? extends ListModel<T>> myGetComboboxModel;
    private final Supplier<? extends ListCellRenderer<? super T>> myGetRenderer;

    private MyBasePopupState(@NotNull Consumer<? super T> onItemSelected,
                             @NotNull Supplier<? extends ListModel<T>> getComboboxModel,
                             @NotNull Supplier<? extends ListCellRenderer<? super T>> getRenderer) {
      super(null, copyItemsFromModel(getComboboxModel.get()));
      myOnItemSelected = onItemSelected;
      myGetComboboxModel = getComboboxModel;
      myGetRenderer = getRenderer;
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
          return new MyBasePopupState<>(myOnItemSelected /* mutable callback! */,
                                        () -> nextModel, myGetRenderer);
        }
      }

      ApplicationManager.getApplication().invokeLater(() -> myOnItemSelected.accept(selectedValue));

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
      final ListCellRenderer<? super T> cellRenderer = myGetRenderer.get();
      Component component = cellRenderer.getListCellRendererComponent(myProxyList, value, -1, false, false);
      return component instanceof TitledSeparator || component instanceof JSeparator ? "" :
             component instanceof JLabel label ? label.getText() :
             component instanceof SimpleColoredComponent c ? c.getCharSequence(false).toString() :
             cellRenderer instanceof Accessible accessible ? accessible.getAccessibleContext().getAccessibleName() :
             String.valueOf(value);
    }

    @Override
    public boolean isSelectable(T value) {
      Component component = myGetRenderer.get().getListCellRendererComponent(myProxyList, value, -1, false, false);
      return !(component instanceof TitledSeparator || component instanceof JSeparator);
    }

    @Override
    public @Nullable ListSeparator getSeparatorAbove(T value) {
      return value instanceof GroupedValue v && v.getSeparatorText() != null
             ? new ListSeparator(v.getSeparatorText())
             : null;
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

  public <T> Boolean isSeparatorAboveOf(T value) {
    return getListModel().isSeparatorAboveOf(value);
  }

  public <T> @NlsContexts.Separator String getCaptionAboveOf(T value) {
    return getListModel().getCaptionAboveOf(value);
  }
}
