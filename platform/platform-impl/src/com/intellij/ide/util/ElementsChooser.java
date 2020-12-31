// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @see ChooseElementsDialog
 */
public class ElementsChooser<T> extends MultiStateElementsChooser<T, Boolean> {
  private static final BooleanMarkStateDescriptor MARK_STATE_DESCRIPTOR = new BooleanMarkStateDescriptor();

  private final Collection<StatisticsCollector<T>> myStatisticsCollectors = new ArrayList<>();

  public void addStatisticsCollector(StatisticsCollector<T> collector) {
    myStatisticsCollectors.add(collector);
    addElementsMarkListener(collector);
  }
  public void removeStatisticsCollector(StatisticsCollector<T> collector) {
    myStatisticsCollectors.remove(collector);
    removeElementsMarkListener(collector);
  }

  public interface ElementsMarkListener<T> {
    void elementMarkChanged(T element, boolean isMarked);
  }

  public interface StatisticsCollector<T> extends ElementsMarkListener<T> {
    void selectionInverted();
    void allSelected();
    void noneSelected();
  }

  public ElementsChooser(final boolean elementsCanBeMarked) {
    super(elementsCanBeMarked, getMarkStateDescriptor());
  }

  public ElementsChooser(List<T> elements, boolean marked) {
    super(elements, marked, getMarkStateDescriptor());
  }

  public void addElementsMarkListener(ElementsMarkListener<T> listener) {
    addElementsMarkListener(new ElementsMarkStateListenerAdapter<>(listener));
  }

  public void removeElementsMarkListener(ElementsMarkListener<T> listener) {
    removeElementsMarkListener(new ElementsMarkStateListenerAdapter<>(listener));
  }

  public void addElement(T element, final boolean isMarked) {
    addElement(element, getMarkState(isMarked));
  }

  /**
   * Check if element is marked
   *
   * @param element an element to test
   * @return true if element is marked
   */
  public boolean isElementMarked(T element) {
    return getElementMarkState(element);
  }

  /**
   * Update element mark
   *
   * @param element an element to test
   * @param marked  a new value of mark.
   */
  public void setElementMarked(T element, boolean marked) {
    setElementMarkState(element, getMarkState(marked));
  }

  public void addElement(T element, final boolean isMarked, ElementProperties elementProperties) {
    addElement(element, getMarkState(isMarked), elementProperties);
  }

  public void setElements(List<T> elements, boolean marked) {
    setElements(elements, getMarkState(marked));
  }

  public void markElements(Collection<T> elements) {
    markElements(elements, Boolean.TRUE);
  }

  @NotNull
  public List<T> getMarkedElements() {
    return getElements(true);
  }

  @NotNull
  public List<T> getElements(boolean isMarked) {
    Map<T, Boolean> elementMarkStates = getElementMarkStates();
    List<T> elements = new ArrayList<>();
    for (Map.Entry<T, Boolean> entry : elementMarkStates.entrySet()) {
      if (entry.getValue() == isMarked) {
        elements.add(entry.getKey());
      }
    }
    return elements;
  }

  public boolean hasUnmarkedElements() {
    Map<T, Boolean> elementMarkStates = getElementMarkStates();
    for (Map.Entry<T, Boolean> entry : elementMarkStates.entrySet()) {
      if (!entry.getValue()) {
        return true;
      }
    }
    return false;
  }

  public void invertSelection() {
    final int count = getElementCount();
    for (int i = 0; i < count; i++) {
      T type = getElementAt(i);
      setElementMarked(type, !isElementMarked(type));
    }
    myStatisticsCollectors.forEach(StatisticsCollector::selectionInverted);
  }

  public void setAllElementsMarked(boolean marked) {
    setAllElementsMarked(getMarkState(marked));
    if (marked) myStatisticsCollectors.forEach(StatisticsCollector::allSelected);
    else myStatisticsCollectors.forEach(StatisticsCollector::noneSelected);
  }

  private static Boolean getMarkState(boolean marked) {
    return marked;
  }

  @SuppressWarnings("unchecked")
  private static <T> MarkStateDescriptor<T, Boolean> getMarkStateDescriptor() {
    return MARK_STATE_DESCRIPTOR;
  }

  private static class BooleanMarkStateDescriptor<T> implements MarkStateDescriptor<T, Boolean> {
    @NotNull
    @Override
    public Boolean getDefaultState(@NotNull T element) {
      return Boolean.FALSE;
    }

    @NotNull
    @Override
    public Boolean getNextState(@NotNull T element, @NotNull Boolean state) {
      return !state;
    }

    @Nullable
    @Override
    public Boolean getNextState(@NotNull Map<T, Boolean> elementsWithStates) {
      boolean currentlyMarked = true;
      for (Boolean state : elementsWithStates.values()) {
        currentlyMarked = state;
        if (!currentlyMarked) {
          break;
        }
      }
      return !currentlyMarked;
    }

    @Override
    public boolean isMarked(@NotNull Boolean state) {
      return state;
    }

    @Nullable
    @Override
    public Boolean getMarkState(@Nullable Object value) {
      return value instanceof Boolean ? ((Boolean)value) : null;
    }

    @Nullable
    @Override
    public TableCellRenderer getMarkRenderer() {
      return null;
    }
  }

  private static class ElementsMarkStateListenerAdapter<T> implements ElementsMarkStateListener<T, Boolean> {
    private final ElementsMarkListener<T> myListener;

    ElementsMarkStateListenerAdapter(ElementsMarkListener<T> listener) {
      myListener = listener;
    }

    @Override
    public void elementMarkChanged(T element, Boolean markState) {
      myListener.elementMarkChanged(element, markState);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ElementsMarkStateListenerAdapter that = (ElementsMarkStateListenerAdapter)o;

      if (!myListener.equals(that.myListener)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myListener.hashCode();
    }
  }
}
