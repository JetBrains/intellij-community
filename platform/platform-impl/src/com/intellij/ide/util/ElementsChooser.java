/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  public interface ElementsMarkListener<T> {
    void elementMarkChanged(T element, boolean isMarked);
  }

  public ElementsChooser(final boolean elementsCanBeMarked) {
    super(elementsCanBeMarked, ElementsChooser.<T>getMarkStateDescriptor());
  }

  public ElementsChooser(List<T> elements, boolean marked) {
    super(elements, marked, ElementsChooser.<T>getMarkStateDescriptor());
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
    Map<T, Boolean> elementMarkStates = getElementMarkStates();
    List<T> elements = new ArrayList<>();
    for (Map.Entry<T, Boolean> entry : elementMarkStates.entrySet()) {
      if (entry.getValue()) {
        elements.add(entry.getKey());
      }
    }
    return elements;
  }

  public void invertSelection() {
    final int count = getElementCount();
    for (int i = 0; i < count; i++) {
      T type = getElementAt(i);
      setElementMarked(type, !isElementMarked(type));
    }
  }

  public void setAllElementsMarked(boolean marked) {
    setAllElementsMarked(getMarkState(marked));
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

    public ElementsMarkStateListenerAdapter(ElementsMarkListener<T> listener) {
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
