// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list;

import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComboBoxPopupState;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.dsl.listCellRenderer.KotlinUIDslRendererComponent;
import com.intellij.ui.dsl.listCellRenderer.LcrRow;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.ScreenReader;
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
  public static final @NotNull JBEmptyBorder COMBO_ITEM_BORDER = JBUI.Borders.empty(2, 8);
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

  private static @NotNull <T> MyBasePopupState<T> popupStateFromContext(@NotNull Context<T> context,
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
      List<T> stepValues = step.getValues();
      step.setDefaultOptionIndex(stepValues.indexOf(selectedItem));
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

  @Override
  protected @NotNull WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    if (step instanceof MyBasePopupState) {
      //noinspection unchecked
      return new ComboBoxPopup<>(myContext, parent, (MyBasePopupState<T>)step, parentValue);
    }

    throw new IllegalArgumentException(step.getClass().toString());
  }

  @Override
  protected @NotNull JComponent createPopupComponent(JComponent content) {
    final var component = super.createPopupComponent(content);
    final var renderer = ((MyBasePopupState<?>)myStep).myGetRenderer.get();
    if (component instanceof JBScrollPane scrollPane && isRendererWithInsets(renderer)) {
      scrollPane.setOverlappingScrollBar(true);
    }
    return component;
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

  public static boolean isRendererWithInsets(ListCellRenderer<?> comboRenderer) {
    ListCellRenderer<?> unwrappedRenderer = unwrap(comboRenderer);
    return ExperimentalUI.isNewUI() && unwrappedRenderer instanceof ExperimentalUI.NewUIComboBoxRenderer;
  }

  private void configurePopup() {
    setMaxRowCount(myContext.getMaximumRowCount());
    setRequestFocus(false);

    JList<T> list = getList();
    myContext.configureList(list);

    list.setSelectionForeground(UIManager.getColor("ComboBox.selectionForeground"));
    list.setSelectionBackground(UIManager.getColor("ComboBox.selectionBackground"));
    list.setBorder(null);
    list.setFocusable(ScreenReader.isActive());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    final var renderer = ((MyBasePopupState<?>)myStep).myGetRenderer.get();
    if (isRendererWithInsets(renderer)) {
      list.setBorder(JBUI.Borders.empty(PopupUtil.getListInsets(false, false)));
      mySpeedSearch.addChangeListener(x -> {
        list.setBorder(JBUI.Borders.empty(PopupUtil.getListInsets(!mySpeedSearch.getFilter().isBlank(), false)));
      });
    } else {
      Border border = UIManager.getBorder("ComboPopup.border");
      if (border != null) {
        getContent().setBorder(border);
      }
    }
  }

  private final class MyDelegateRenderer implements ListCellRenderer<T> {
    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      //noinspection unchecked
      Component component = myContext.getRenderer().getListCellRendererComponent(list, (T)value, index, isSelected, cellHasFocus);
      if (component instanceof JComponent jComponent && !(component instanceof JSeparator || component instanceof TitledSeparator)) {
        if (!(component instanceof GroupedElementsRenderer.MyComponent)
            && !(component instanceof KotlinUIDslRendererComponent)
            && !(component instanceof DarculaComboBoxRenderer)) {
          jComponent.setBorder(COMBO_ITEM_BORDER);
        }
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

    @Override
    public @Nullable PopupStep<?> onChosen(T selectedValue, boolean finalChoice) {
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

    @Override
    public @NotNull String getTextFor(T value) {
      final ListCellRenderer<? super T> cellRenderer = myGetRenderer.get();
      Component component = cellRenderer.getListCellRendererComponent(myProxyList, value, -1, false, false);
      String componentText = component instanceof TitledSeparator || component instanceof JSeparator ? "" :
                             component instanceof JLabel label ? label.getText() :
                             component instanceof SimpleColoredComponent c ? c.getCharSequence(false).toString() :
                             component instanceof Accessible accessible ? accessible.getAccessibleContext().getAccessibleName() :
                             null;
      return componentText != null ? componentText : String.valueOf(value);
    }

    @Override
    public boolean isSelectable(T value) {
      Component component = myGetRenderer.get().getListCellRendererComponent(myProxyList, value, -1, false, false);
      return !((component instanceof ComboBox.SelectableItem selectableItem && !selectableItem.isSelectable())
               || component instanceof JSeparator);
    }

    @Override
    public @Nullable ListSeparator getSeparatorAbove(T value) {
      final ListCellRenderer<? super T> cellRenderer = myGetRenderer.get();
      if (cellRenderer instanceof GroupedComboBoxRenderer<? super T> renderer) {
        return renderer.separatorFor(value);
      }
      if (cellRenderer instanceof ComboBoxWithWidePopup<? super T>.AdjustingListCellRenderer renderer) {
        ListCellRenderer<? super T> delegate = renderer.delegate;
        if (delegate instanceof GroupedComboBoxRenderer<? super T> groupedComboBoxRenderer) {
          return groupedComboBoxRenderer.separatorFor(value);
        }
      }
      ListCellRenderer unwrappedRenderer = unwrap(cellRenderer);
      if (unwrappedRenderer instanceof LcrRow<?>) {
        //noinspection unchecked
        KotlinUIDslRendererComponent component =
          (KotlinUIDslRendererComponent)unwrappedRenderer.getListCellRendererComponent(myProxyList, value, -1, false, false);
        return component.getListSeparator();
      }

      return null;
    }
  }

  private static ListCellRenderer unwrap(ListCellRenderer renderer) {
    while (renderer != null) {
      if (renderer instanceof ComboBoxWithWidePopup.AdjustingListCellRenderer wrapper) {
        renderer = wrapper.delegate;
      } else {
        return renderer;
      }
    }
    return null;
  }

  private static @NotNull <T> List<T> copyItemsFromModel(@NotNull ListModel<T> model) {
    ArrayList<T> items = new ArrayList<>(model.getSize());
    for (int i = 0, size = model.getSize(); i < size; i++) {
      items.add(model.getElementAt(i));
    }
    return items;
  }

  public <U> Boolean isSeparatorAboveOf(U value) {
    return getListModel().isSeparatorAboveOf(value);
  }

  public <U> @NlsContexts.Separator String getCaptionAboveOf(U value) {
    return getListModel().getCaptionAboveOf(value);
  }
}
